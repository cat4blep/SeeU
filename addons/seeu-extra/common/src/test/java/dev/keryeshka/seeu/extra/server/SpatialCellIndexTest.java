package dev.keryeshka.seeu.extra.server;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpatialCellIndexTest {
    private static final Comparator<PointDistance> NEAREST_FIRST = Comparator
            .comparingDouble(PointDistance::distanceSquared)
            .thenComparingInt(value -> value.point().id());

    @Test
    void visitNearbyOnlyVisitsOccupiedCellsInsideTheMaximumDistance() {
        SpatialCellIndex<String> index = new SpatialCellIndex<>();
        index.add("origin", 1.0D, 1.0D, 1.0D);
        index.add("near", 17.0D, 1.0D, 1.0D);
        index.add("far", 49.0D, 1.0D, 1.0D);
        index.add("outside", 96.0D, 1.0D, 1.0D);

        List<String> visited = new ArrayList<>();
        index.visitNearby(0.0D, 0.0D, 64, () -> square(64), visited::addAll);

        assertEquals(List.of("origin", "near", "far"), visited);
    }

    @Test
    void tighterCutoffStopsBeforeDistantCells() {
        SpatialCellIndex<String> index = new SpatialCellIndex<>();
        index.add("selected", 8.0D, 1.0D, 8.0D);
        index.add("distant", 49.0D, 1.0D, 8.0D);
        AtomicReference<Double> cutoffSquared = new AtomicReference<>(square(64));

        List<String> visited = new ArrayList<>();
        index.visitNearby(8.0D, 8.0D, 64, cutoffSquared::get, cell -> {
            visited.addAll(cell);
            cutoffSquared.set(1.0D);
        });

        assertEquals(List.of("selected"), visited);
    }

    @Test
    void cutoffIncludesCellsOnTheExactDistanceBoundary() {
        SpatialCellIndex<String> index = new SpatialCellIndex<>();
        index.add("center", 8.0D, 1.0D, 8.0D);
        index.add("boundary", 17.0D, 1.0D, 8.0D);

        List<String> visited = new ArrayList<>();
        index.visitNearby(8.0D, 8.0D, 64, () -> square(8), visited::addAll);

        assertEquals(List.of("center", "boundary"), visited);
    }

    @Test
    void negativeCoordinatesUseFloorBasedCells() {
        SpatialCellIndex<String> index = new SpatialCellIndex<>();
        index.add("negative", -0.5D, -0.5D, -0.5D);
        index.add("positive", 0.5D, 0.5D, 0.5D);

        List<String> visited = new ArrayList<>();
        index.visitNearby(-0.25D, -0.25D, 1, () -> 1.0D, visited::addAll);

        assertEquals(List.of("negative", "positive"), visited);
    }

    @Test
    void dynamicCutoffProducesTheSameNearestSetAsAFullScan() {
        Random random = new Random(0x5EE0L);
        List<Point> points = new ArrayList<>();
        SpatialCellIndex<Point> index = new SpatialCellIndex<>();
        for (int id = 0; id < 2_000; id++) {
            Point point = new Point(
                    id,
                    random.nextDouble() * 2_000.0D - 1_000.0D,
                    random.nextDouble() * 400.0D - 200.0D,
                    random.nextDouble() * 2_000.0D - 1_000.0D
            );
            points.add(point);
            index.add(point, point.x(), point.y(), point.z());
        }

        double viewerX = 3.25D;
        double viewerY = 70.0D;
        double viewerZ = -8.5D;
        int minimumDistance = 96;
        int maximumDistance = 512;
        int cap = 17;
        double minimumDistanceSquared = square(minimumDistance);
        double maximumDistanceSquared = square(maximumDistance);
        PriorityQueue<PointDistance> nearest = new PriorityQueue<>(cap, NEAREST_FIRST.reversed());

        index.visitNearby(
                viewerX,
                viewerZ,
                maximumDistance,
                () -> nearest.size() == cap
                        ? nearest.peek().distanceSquared()
                        : maximumDistanceSquared,
                cell -> {
                    for (Point point : cell) {
                        double distanceSquared = point.distanceSquared(viewerX, viewerY, viewerZ);
                        if (distanceSquared < minimumDistanceSquared
                                || distanceSquared > maximumDistanceSquared) {
                            continue;
                        }
                        PointDistance ranked = new PointDistance(point, distanceSquared);
                        if (nearest.size() < cap) {
                            nearest.add(ranked);
                        } else if (NEAREST_FIRST.compare(ranked, nearest.peek()) < 0) {
                            nearest.remove();
                            nearest.add(ranked);
                        }
                    }
                }
        );

        List<PointDistance> expected = points.stream()
                .map(point -> new PointDistance(
                        point,
                        point.distanceSquared(viewerX, viewerY, viewerZ)
                ))
                .filter(value -> value.distanceSquared() >= minimumDistanceSquared
                        && value.distanceSquared() <= maximumDistanceSquared)
                .sorted(NEAREST_FIRST)
                .limit(cap)
                .toList();
        List<PointDistance> actual = nearest.stream().sorted(NEAREST_FIRST).toList();

        assertEquals(expected, actual);
    }

    private static double square(int value) {
        return (double) value * value;
    }

    private record Point(int id, double x, double y, double z) {
        private double distanceSquared(double otherX, double otherY, double otherZ) {
            double deltaX = x - otherX;
            double deltaY = y - otherY;
            double deltaZ = z - otherZ;
            return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
        }
    }

    private record PointDistance(Point point, double distanceSquared) {
    }
}
