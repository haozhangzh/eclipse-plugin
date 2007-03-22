/*******************************************************************************
 * Copyright 2005, CHISEL Group, University of Victoria, Victoria, BC, Canada.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: The Chisel Group, University of Victoria
 ******************************************************************************/
package org.eclipse.mylar.zest.core.widgets;

import java.util.HashMap;

import org.eclipse.draw2d.ChopboxAnchor;
import org.eclipse.draw2d.ColorConstants;
import org.eclipse.draw2d.Connection;
import org.eclipse.draw2d.Graphics;
import org.eclipse.draw2d.PolygonDecoration;
import org.eclipse.draw2d.PolylineConnection;
import org.eclipse.draw2d.Shape;
import org.eclipse.mylar.zest.core.ZestStyles;
import org.eclipse.mylar.zest.core.widgets.internal.PolylineArcConnection;
import org.eclipse.mylar.zest.core.widgets.internal.RoundedChopboxAnchor;
import org.eclipse.mylar.zest.layouts.LayoutBendPoint;
import org.eclipse.mylar.zest.layouts.LayoutEntity;
import org.eclipse.mylar.zest.layouts.LayoutRelationship;
import org.eclipse.mylar.zest.layouts.constraints.LayoutConstraint;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Display;

/**
 * This is the graph connection model which stores the source and destination
 * nodes and the properties of this connection (color, line width etc).
 * 
 * @author Chris Callendar
 */
public class GraphConnection extends GraphItem implements IGraphConnection, LayoutRelationship {

	private Font font;
	private IGraphNode sourceNode;
	private IGraphNode destinationNode;

	private double weight;
	private Color color;
	private Color highlightColor;
	private Color foreground;
	private int lineWidth;
	private int lineStyle;
	private final HashMap attributes;
	private final Graph graphModel;

	private Object internalConnection;
	private int connectionStyle;
	private int curveDepth;
	private boolean isDisposed = false;

	private Connection connectionFigure = null;

	/**
	 * For bezier curves: angle between the start point, and the line. This may
	 * be a hint only. Future implementations of graph viewers may adjust the
	 * actual visual representation based on the look of the graph.
	 */
	// @tag zest(bug(152530-Bezier(fix)))
	private double startAngle;
	/**
	 * For bezier curves: angle between the end point and the line. This may be
	 * a hint only. Future implementations of graph viewers may adjust the
	 * actual visual representation based on the look of the graph.
	 */
	// @tag zest(bug(152530-Bezier(fix)))
	private double endAngle;

	/**
	 * For bezier curves: this is a value from 0-1 as a ratio of the length of
	 * the line between the start point, and the control point/the length of the
	 * connection.
	 */
	// @tag zest(bug(152530-Bezier(fix)))
	private double startLength;

	/**
	 * For bezier curves: this is a value from 0-1 as a ratio of the length of
	 * the line between the end point, and the control point/the length of the
	 * connection.
	 */
	// @tag zest(bug(152530-Bezier(fix)))
	private double endLength;

	/**
	 * The state of visibility set by the user.
	 */
	private boolean visible;

	private boolean highlighted;

	public GraphConnection(Graph graphModel, int style, IGraphNode source, IGraphNode destination) {
		super(graphModel);

		this.connectionStyle |= graphModel.getConnectionStyle();
		this.sourceNode = source;
		this.destinationNode = destination;
		this.visible = true;
		this.color = ColorConstants.lightGray;
		this.foreground = ColorConstants.lightGray;
		this.highlightColor = graphModel.DARK_BLUE;
		this.lineWidth = 1;
		this.lineStyle = Graphics.LINE_SOLID;
		setWeightInLayout(weight);
		this.attributes = new HashMap();
		this.graphModel = graphModel;
		this.sourceNode = source;
		this.destinationNode = destination;
		this.curveDepth = 20;
		this.font = Display.getDefault().getSystemFont();
		((GraphNode) source).addSourceConnection(this);
		((GraphNode) destination).addTargetConnection(this);
		connectionFigure = createFigure();

		graphModel.addConnection(this);
	}

	public void dispose() {
		super.dispose();
		this.isDisposed = true;
		((GraphNode) getSource()).removeSourceConnection(this);
		((GraphNode) getDestination()).removeTargetConnection(this);
		graphModel.removeConnection(this);
	}

	public boolean isDisposed() {
		return isDisposed;
	}

	public Connection getConnectionFigure() {
		return connectionFigure;
	}

	/**
	 * Gets the external connection object.
	 * 
	 * @return Object
	 */
	public Object getExternalConnection() {
		return this.getData();
	}

	/**
	 * Returns a string like 'source -> destination'
	 * 
	 * @return String
	 */
	public String toString() {
		String arrow = (isBidirectionalInLayout() ? " <--> " : " --> ");
		String src = (sourceNode != null ? sourceNode.getText() : "null");
		String dest = (destinationNode != null ? destinationNode.getText() : "null");
		String weight = "  (weight=" + getWeightInLayout() + ")";
		return ("GraphModelConnection: " + src + arrow + dest + weight);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.mylar.zest.layouts.LayoutRelationship#getSourceInLayout()
	 */
	public LayoutEntity getSourceInLayout() {
		return sourceNode;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.mylar.zest.layouts.LayoutRelationship#getDestinationInLayout()
	 */
	public LayoutEntity getDestinationInLayout() {
		return destinationNode;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.mylar.zest.layouts.LayoutRelationship#isBidirectionalInLayout()
	 */
	public boolean isBidirectionalInLayout() {
		return !ZestStyles.checkStyle(connectionStyle, ZestStyles.CONNECTIONS_DIRECTED);
	}

	/**
	 * Returns the style of this connection. Valid styles are those that begin
	 * with CONNECTION in ZestStyles.
	 * 
	 * @return the style of this connection.
	 * @see #ZestStyles
	 */
	public int getConnectionStyle() {
		return connectionStyle;
	}

	/**
	 * Returns the style of this connection. Valid styles are those that begin
	 * with CONNECTION in ZestStyles.
	 * 
	 * @return the style of this connection.
	 * @see #ZestStyles
	 */
	public void setConnectionStyle(int style) {
		this.connectionStyle = style;
	}

	/**
	 * Gets the weight of this connection. The weight must be in {-1, [0-1]}. A
	 * weight of -1 means that there is no force/tension between the nodes. A
	 * weight of 0 results in the maximum spring length being used (farthest
	 * apart). A weight of 1 results in the minimum spring length being used
	 * (closest together).
	 * 
	 * @see org.eclipse.mylar.zest.layouts.LayoutRelationship#getWeightInLayout()
	 * @return the weight: {-1, [0 - 1]}.
	 */
	public double getWeightInLayout() {
		return weight;
	}

	/**
	 * Gets the font for the label on this connection
	 * 
	 * @return
	 */
	public Font getFont() {
		return this.font;
	}

	/**
	 * Sets the font for the label on this connection.
	 * 
	 */
	public void setFont(Font f) {
		this.font = f;
	}

	/**
	 * Sets the weight for this connection. The weight must be in {-1, [0-1]}. A
	 * weight of -1 means that there is no force/tension between the nodes. A
	 * weight of 0 results in the maximum spring length being used (farthest
	 * apart). A weight of 1 results in the minimum spring length being used
	 * (closest together).
	 * 
	 * @see org.eclipse.mylar.zest.layouts.LayoutRelationship#setWeightInLayout(double)
	 */
	public void setWeightInLayout(double weight) {
		if (weight < 0) {
			this.weight = -1;
		} else if (weight > 1) {
			this.weight = 1;
		} else {
			this.weight = weight;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.mylar.zest.layouts.LayoutRelationship#getAttributeInLayout(java.lang.String)
	 */
	public Object getAttributeInLayout(String attribute) {
		return attributes.get(attribute);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.mylar.zest.layouts.LayoutRelationship#setAttributeInLayout(java.lang.String,
	 *      java.lang.Object)
	 */
	public void setAttributeInLayout(String attribute, Object value) {
		attributes.put(attribute, value);
	}

	/**
	 * Returns the color of this connection.
	 * 
	 * @return Color
	 */
	public Color getLineColor() {
		return color;
	}

	/**
	 * Sets the highlight color.
	 * 
	 * @param color
	 *            the color to use for highlighting.
	 */
	public void setHighlightColor(Color color) {
		this.highlightColor = color;
	}

	/**
	 * @return the highlight color
	 */
	public Color getHighlightColor() {
		return highlightColor;
	}

	/**
	 * Perminently sets the color of this line to the given color. This will
	 * become the color of the line when it is not highlighted. If you would
	 * like to temporarily change the color of the line, use changeLineColor.
	 * 
	 * @param color
	 *            the color to be set.
	 * @see changeLineColor(Color color)
	 */
	public void setLineColor(Color color) {
		this.foreground = color;
		changeLineColor(foreground);
	}

	/**
	 * Sets the connection color.
	 * 
	 * @param color
	 */
	public void changeLineColor(Color color) {
		this.color = color;
		updateFigure(connectionFigure);
	}

	/**
	 * Returns the connection line width.
	 * 
	 * @return int
	 */
	public int getLineWidth() {
		return lineWidth;
	}

	/**
	 * Sets the connection line width.
	 * 
	 * @param lineWidth
	 */
	public void setLineWidth(int lineWidth) {
		this.lineWidth = lineWidth;
		updateFigure(connectionFigure);
	}

	/**
	 * Returns the connection line style.
	 * 
	 * @return int
	 */
	public int getLineStyle() {
		return lineStyle;
	}

	/**
	 * Sets the connection line style.
	 * 
	 * @param lineStyle
	 */
	public void setLineStyle(int lineStyle) {
		this.lineStyle = lineStyle;
		updateFigure(connectionFigure);
	}

	/**
	 * Gets the source node for this relationship
	 * 
	 * @return GraphModelNode
	 */
	public IGraphNode getSource() {
		return this.sourceNode;
	}

	/**
	 * Gets the target node for this relationship
	 * 
	 * @return GraphModelNode
	 */
	public IGraphNode getDestination() {
		return this.destinationNode;
	}

	/**
	 * Gets the internal relationship object.
	 * 
	 * @return Object
	 */
	public Object getLayoutInformation() {
		return internalConnection;
	}

	/**
	 * Sets the internal relationship object.
	 * 
	 * @param layoutInformation
	 */
	public void setLayoutInformation(Object layoutInformation) {
		this.internalConnection = layoutInformation;
	}

	/**
	 * Highlights this node. Uses the default highlight color.
	 */
	public void highlight() {
		if (highlighted) {
			return;
		}
		highlighted = true;
		updateFigure(connectionFigure);
		graphModel.highlightEdge(this);
	}

	/**
	 * Unhighlights this node. Uses the default color.
	 */
	public void unhighlight() {
		if (!highlighted) {
			return;
		}
		highlighted = false;
		updateFigure(connectionFigure);
		graphModel.unhighlightEdge(this);
	}

	/**
	 * Returns true if this connection is highlighted, false otherwise
	 * 
	 * @return
	 */
	public boolean isHighlighted() {
		return highlighted;
	}

	/**
	 * Gets the graph model that this connection is in
	 * 
	 * @return The graph model that this connection is contained in
	 */
	public Graph getGraphModel() {
		return this.graphModel;
	}

	public void setBendPoints(LayoutBendPoint[] bendPoints) {

	}

	public void clearBendPoints() {

	}

	/**
	 * Returns the curve depth for this connection. The return value is only
	 * meaningful if the connection style has the ZestStyles.CONNECTIONS_CURVED
	 * style set.
	 * 
	 * @return the curve depth.
	 */
	public int getCurveDepth() {
		return curveDepth;
	}

	public void setCurveDepth(int curveDepth) {
		this.curveDepth = curveDepth;
		updateFigure(connectionFigure);
	}

	public void populateLayoutConstraint(LayoutConstraint constraint) {

	}

	/**
	 * Gets the end angle for bezier arcs.
	 * 
	 * For bezier curves: angle between the start point, and the line. This may
	 * be a hint only. Future implementations of graph viewers may adjust the
	 * actual visual representation based on the look of the graph.
	 */
	// @tag zest(bug(152530-Bezier(fix)))
	public double getEndAngle() {
		return endAngle;
	}

	/**
	 * Sets the end angle for bezier arcs.
	 * 
	 * For bezier curves: angle between the start point, and the line. This may
	 * be a hint only. Future implementations of graph viewers may adjust the
	 * actual visual representation based on the look of the graph.
	 * 
	 * @param endAngle
	 *            the angle to set.
	 */
	// @tag zest(bug(152530-Bezier(fix)))
	public void setEndAngle(double endAngle) {
		this.endAngle = endAngle;
		updateFigure(connectionFigure);
	}

	/**
	 * For bezier curves: this is a value from 0-1 as a ratio of the length of
	 * the line between the end point, and the control point/the length of the
	 * connection.
	 */
	// @tag zest(bug(152530-Bezier(fix)))
	public double getEndLength() {
		return endLength;
	}

	/**
	 * For bezier curves: this is a value from 0-1 as a ratio of the length of
	 * the line between the end point, and the control point/the length of the
	 * connection.
	 * 
	 * @param endLength
	 *            the length to set.
	 */
	// @tag zest(bug(152530-Bezier(fix)))
	public void setEndLength(double endLength) {
		this.endLength = endLength;
		updateFigure(connectionFigure);
	}

	/**
	 * Gets the start angle for bezier arcs.
	 * 
	 * For bezier curves: angle between the end point and the line. This may be
	 * a hint only. Future implementations of graph viewers may adjust the
	 * actual visual representation based on the look of the graph.
	 */
	// @tag zest(bug(152530-Bezier(fix)))
	public double getStartAngle() {
		return startAngle;
	}

	/**
	 * Sets the start angle for bezier arcs.
	 * 
	 * For bezier curves: angle between the end point and the line. This may be
	 * a hint only. Future implementations of graph viewers may adjust the
	 * actual visual representation based on the look of the graph.
	 */
	// @tag zest(bug(152530-Bezier(fix)))
	public void setStartAngle(double startAngle) {
		this.startAngle = startAngle;
		updateFigure(connectionFigure);

	}

	/**
	 * For bezier curves: this is a value from 0-1 as a ratio of the length of
	 * the line between the start point, and the control point/the length of the
	 * connection.
	 */
	// @tag zest(bug(152530-Bezier(fix)))
	public double getStartLength() {
		return startLength;
	}

	/**
	 * For bezier curves: this is a value from 0-1 as a ratio of the length of
	 * the line between the start point, and the control point/the length of the
	 * connection.
	 * 
	 * @param startLength
	 *            the length to set.
	 */
	// @tag zest(bug(152530-Bezier(fix)))
	public void setStartLength(double startLength) {
		this.startLength = startLength;
		updateFigure(connectionFigure);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.mylar.zest.core.widgets.IGraphItem#getItemType()
	 */
	public int getItemType() {
		return CONNECTION;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.mylar.zest.core.internal.graphmodel.GraphItem#setVisible(boolean)
	 */
	public void setVisible(boolean visible) {
		graphModel.setItemVisible(this, visible);
		this.visible = visible;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.mylar.zest.core.widgets.IGraphItem#isVisible()
	 */
	public boolean isVisible() {
		return visible;
	}

	private Connection updateFigure(Connection connection) {
		Shape connectionShape = (Shape) connection;
		connectionShape.setLineWidth(getLineWidth());
		connectionShape.setLineStyle(getLineStyle());
		if (highlighted) {
			connectionShape.setForegroundColor(getHighlightColor());
		} else {
			connectionShape.setForegroundColor(getLineColor());
		}

		if (connection instanceof PolylineArcConnection) {
			PolylineArcConnection arcConnection = (PolylineArcConnection) connection;
			arcConnection.setDepth(getCurveDepth());
		}
		if ((connectionStyle & ZestStyles.CONNECTIONS_DIRECTED) > 0) {
			PolygonDecoration decoration = new PolygonDecoration();
			if (getLineWidth() < 1) {
				decoration.setScale(7, 3);
			} else {
				double logLineWith = getLineWidth() / 2.0;
				decoration.setScale(7 * logLineWith, 3 * logLineWith);
			}
			((PolylineConnection) connection).setTargetDecoration(decoration);
		}
		return connection;
	}

	private Connection createFigure() {
		Connection connectionFigure = null;
		if (getSource() == getDestination()) {
			connectionFigure = new PolylineArcConnection();
		} else {
			connectionFigure = new PolylineConnection();
		}
		ChopboxAnchor sourceAnchor = new RoundedChopboxAnchor(getSource().getNodeFigure(), 8);
		ChopboxAnchor targetAnchor = new RoundedChopboxAnchor(getDestination().getNodeFigure(), 8);
		connectionFigure.setSourceAnchor(sourceAnchor);
		connectionFigure.setTargetAnchor(targetAnchor);

		return updateFigure(connectionFigure);
	}
}
