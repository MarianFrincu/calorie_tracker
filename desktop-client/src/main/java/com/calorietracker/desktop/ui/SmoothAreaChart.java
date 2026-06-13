package com.calorietracker.desktop.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.Axis;
import javafx.scene.chart.XYChart;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;

/**
 * {@link AreaChart} that draws the line through its data points as smooth
 * cubic Bezier curves (Catmull-Rom spline) instead of the default straight
 * polyline. The area fill underneath inherits the same curve, so the top of
 * the filled region matches the curve.
 *
 * <p>JavaFX builds line / fill paths after every layout pass; we hook in by
 * overriding {@code layoutPlotChildren()} and rewriting the path elements
 * <i>after</i> the superclass has computed each data point's screen position.
 */
public class SmoothAreaChart<X, Y> extends AreaChart<X, Y> {

    public SmoothAreaChart(Axis<X> xAxis, Axis<Y> yAxis) {
        super(xAxis, yAxis);
    }

    @Override
    protected void layoutPlotChildren() {
        super.layoutPlotChildren();
        for (XYChart.Series<X, Y> series : getData()) {
            smoothSeries(series);
        }
    }

    private void smoothSeries(XYChart.Series<X, Y> series) {
        Node seriesNode = series.getNode();
        if (!(seriesNode instanceof Group group) || group.getChildren().size() < 2) return;

        // AreaChart's series node is a Group with [fillPath, linePath] in that order.
        Node n0 = group.getChildren().get(0);
        Node n1 = group.getChildren().get(1);
        Path fillPath = n0 instanceof Path ? (Path) n0 : null;
        Path linePath = n1 instanceof Path ? (Path) n1 : null;
        if (linePath == null) return;

        // Use the axes' display positions directly. node.getLayoutX/Y returns
        // the TOP-LEFT of the symbol box; the symbol's centre is offset by
        // half its width/height, which made the smooth curve sit a few pixels
        // away from the markers. Going through the axes is exactly what
        // AreaChart's own line-path code does, so the curve lines up with the
        // markers regardless of symbol size.
        List<double[]> positions = new ArrayList<>();
        for (XYChart.Data<X, Y> d : series.getData()) {
            if (d.getXValue() == null || d.getYValue() == null) continue;
            double x = getXAxis().getDisplayPosition(d.getXValue());
            double y = getYAxis().getDisplayPosition(d.getYValue());
            if (Double.isNaN(x) || Double.isNaN(y)) continue;
            positions.add(new double[]{x, y});
        }
        if (positions.size() < 2) return;

        // Compute monotonic tangents once - shared between the line and fill paths.
        double[] tangents = monotonicTangents(positions);

        // ---- Smooth the line path -----------------------------------------
        List<PathElement> lineEls = new ArrayList<>();
        lineEls.add(new MoveTo(positions.get(0)[0], positions.get(0)[1]));
        addMonotonicCurves(lineEls, positions, tangents);
        linePath.getElements().setAll(lineEls);

        // ---- Smooth the fill path -----------------------------------------
        if (fillPath != null) {
            double baselineY = getYAxis().getHeight();
            double firstX    = positions.get(0)[0];
            double lastX     = positions.get(positions.size() - 1)[0];

            List<PathElement> fillEls = new ArrayList<>();
            // Match the JavaFX-default fill outline: start at (0, baseline),
            // diagonal into the first data point, then trace the smooth curve
            // through every subsequent point, then drop back down to baseline.
            fillEls.add(new MoveTo(0, baselineY));
            fillEls.add(new LineTo(firstX, positions.get(0)[1]));
            addMonotonicCurves(fillEls, positions, tangents);
            fillEls.add(new LineTo(lastX, baselineY));
            fillEls.add(new LineTo(0,     baselineY));
            fillEls.add(new ClosePath());
            fillPath.getElements().setAll(fillEls);
        }
    }

    /**
     * Compute the tangent (dy/dx) at each data point using the Fritsch-Carlson
     * monotone cubic algorithm.
     *
     * <p>The crucial bit vs a plain Catmull-Rom spline: at every <i>local
     * extremum</i> (where the sequence changes direction) we force the
     * tangent to zero. That stops the classic Catmull-Rom artefact where
     * two consecutive equal points (e.g. two days at 0 kcal) still produce
     * a curve that dips below them, because the neighbour points keep
     * pulling the spline.
     */
    private static double[] monotonicTangents(List<double[]> p) {
        int n = p.size();
        double[] delta = new double[n - 1];
        double[] dx    = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            dx[i] = p.get(i + 1)[0] - p.get(i)[0];
            double dy = p.get(i + 1)[1] - p.get(i)[1];
            delta[i] = dx[i] == 0 ? 0 : dy / dx[i];
        }
        double[] m = new double[n];
        m[0]     = delta[0];
        m[n - 1] = delta[n - 2];
        for (int i = 1; i < n - 1; i++) {
            // Sign change OR either side flat -> flatten the tangent (monotone preservation).
            if (delta[i - 1] * delta[i] <= 0) {
                m[i] = 0;
            } else {
                m[i] = (delta[i - 1] + delta[i]) / 2.0;
            }
        }
        // Hyman / Fritsch-Carlson monotonicity correction.
        for (int i = 0; i < n - 1; i++) {
            if (delta[i] == 0) {
                m[i] = 0;
                m[i + 1] = 0;
                continue;
            }
            double a = m[i]     / delta[i];
            double b = m[i + 1] / delta[i];
            double h = Math.hypot(a, b);
            if (h > 3) {
                double t = 3.0 / h;
                m[i]     = t * a * delta[i];
                m[i + 1] = t * b * delta[i];
            }
        }
        return m;
    }

    /** Emit one {@link CubicCurveTo} per segment using the precomputed tangents. */
    private static void addMonotonicCurves(List<PathElement> out, List<double[]> p, double[] m) {
        for (int i = 0; i < p.size() - 1; i++) {
            double x1 = p.get(i)[0],     y1 = p.get(i)[1];
            double x2 = p.get(i + 1)[0], y2 = p.get(i + 1)[1];
            double h  = (x2 - x1) / 3.0;
            double c1x = x1 + h;
            double c1y = y1 + m[i]     * h;
            double c2x = x2 - h;
            double c2y = y2 - m[i + 1] * h;
            out.add(new CubicCurveTo(c1x, c1y, c2x, c2y, x2, y2));
        }
    }
}
