package dev.keryeshka.seeu.extra.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

/**
 * A sparse horizontal cell index rebuilt from the loaded entities for one broadcast pass.
 */
final class SpatialCellIndex<T> {
    static final int CELL_SIZE_BLOCKS = 16;

    private final Map<Long, CellBucket<T>> cells = new HashMap<>();
    private SpatialNode<T> root;

    void add(T value, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return;
        }

        int cellX = cellCoordinate(x);
        int cellZ = cellCoordinate(z);
        cells.computeIfAbsent(
                cellKey(cellX, cellZ),
                ignored -> new CellBucket<>(cellX, cellZ)
        ).values.add(value);
        root = null;
    }

    void visitNearby(
            double x,
            double z,
            int maximumDistanceBlocks,
            DoubleSupplier cutoffSquared,
            Consumer<List<T>> visitor
    ) {
        if (!Double.isFinite(x)
                || !Double.isFinite(z)
                || maximumDistanceBlocks < 0
                || cells.isEmpty()) {
            return;
        }

        SpatialNode<T> spatialRoot = root();
        double maximumDistanceSquared = square(maximumDistanceBlocks);
        double rootDistanceSquared = spatialRoot.minimumHorizontalDistanceSquared(x, z);
        if (rootDistanceSquared > maximumDistanceSquared) {
            return;
        }

        PriorityQueue<NodeVisit<T>> pending = new PriorityQueue<>(SpatialCellIndex::compareVisits);
        pending.add(new NodeVisit<>(spatialRoot, rootDistanceSquared));
        while (!pending.isEmpty()) {
            double effectiveCutoffSquared = effectiveCutoffSquared(
                    maximumDistanceSquared,
                    cutoffSquared.getAsDouble()
            );
            NodeVisit<T> next = pending.peek();
            if (next.minimumDistanceSquared() > effectiveCutoffSquared) {
                return;
            }
            pending.remove();

            SpatialNode<T> node = next.node();
            if (node.bucket != null) {
                visitor.accept(node.bucket.values);
                continue;
            }
            offer(node.left, x, z, effectiveCutoffSquared, pending);
            offer(node.right, x, z, effectiveCutoffSquared, pending);
        }
    }

    private SpatialNode<T> root() {
        if (root == null) {
            List<CellBucket<T>> buckets = new ArrayList<>(cells.values());
            root = build(buckets, 0, buckets.size());
        }
        return root;
    }

    private static <T> SpatialNode<T> build(List<CellBucket<T>> buckets, int fromIndex, int toIndex) {
        if (toIndex - fromIndex == 1) {
            return SpatialNode.leaf(buckets.get(fromIndex));
        }

        int minimumCellX = Integer.MAX_VALUE;
        int maximumCellX = Integer.MIN_VALUE;
        int minimumCellZ = Integer.MAX_VALUE;
        int maximumCellZ = Integer.MIN_VALUE;
        for (int index = fromIndex; index < toIndex; index++) {
            CellBucket<T> bucket = buckets.get(index);
            minimumCellX = Math.min(minimumCellX, bucket.cellX);
            maximumCellX = Math.max(maximumCellX, bucket.cellX);
            minimumCellZ = Math.min(minimumCellZ, bucket.cellZ);
            maximumCellZ = Math.max(maximumCellZ, bucket.cellZ);
        }

        long spanX = (long) maximumCellX - minimumCellX;
        long spanZ = (long) maximumCellZ - minimumCellZ;
        boolean splitOnX = spanX >= spanZ;
        int splitCoordinate = splitOnX
                ? (int) (minimumCellX + spanX / 2L)
                : (int) (minimumCellZ + spanZ / 2L);
        int midpoint = partition(
                buckets,
                fromIndex,
                toIndex,
                splitOnX,
                splitCoordinate
        );
        return SpatialNode.branch(
                build(buckets, fromIndex, midpoint),
                build(buckets, midpoint, toIndex)
        );
    }

    private static int partition(
            List<? extends CellBucket<?>> buckets,
            int fromIndex,
            int toIndex,
            boolean splitOnX,
            int splitCoordinate
    ) {
        int leftIndex = fromIndex;
        int rightIndex = toIndex - 1;
        while (leftIndex <= rightIndex) {
            while (leftIndex <= rightIndex
                    && coordinate(buckets.get(leftIndex), splitOnX) <= splitCoordinate) {
                leftIndex++;
            }
            while (leftIndex <= rightIndex
                    && coordinate(buckets.get(rightIndex), splitOnX) > splitCoordinate) {
                rightIndex--;
            }
            if (leftIndex < rightIndex) {
                swap(buckets, leftIndex, rightIndex);
                leftIndex++;
                rightIndex--;
            }
        }
        return leftIndex;
    }

    private static int coordinate(CellBucket<?> bucket, boolean useX) {
        return useX ? bucket.cellX : bucket.cellZ;
    }

    private static <T> void swap(List<T> values, int leftIndex, int rightIndex) {
        T left = values.get(leftIndex);
        values.set(leftIndex, values.get(rightIndex));
        values.set(rightIndex, left);
    }

    private static <T> void offer(
            SpatialNode<T> node,
            double x,
            double z,
            double cutoffSquared,
            PriorityQueue<NodeVisit<T>> pending
    ) {
        double minimumDistanceSquared = node.minimumHorizontalDistanceSquared(x, z);
        if (minimumDistanceSquared <= cutoffSquared) {
            pending.add(new NodeVisit<>(node, minimumDistanceSquared));
        }
    }

    private static int compareVisits(NodeVisit<?> left, NodeVisit<?> right) {
        int comparison = Double.compare(left.minimumDistanceSquared(), right.minimumDistanceSquared());
        if (comparison != 0) {
            return comparison;
        }
        SpatialNode<?> leftNode = left.node();
        SpatialNode<?> rightNode = right.node();
        comparison = Integer.compare(leftNode.minimumCellX, rightNode.minimumCellX);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(leftNode.minimumCellZ, rightNode.minimumCellZ);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(leftNode.maximumCellX, rightNode.maximumCellX);
        if (comparison != 0) {
            return comparison;
        }
        return Integer.compare(leftNode.maximumCellZ, rightNode.maximumCellZ);
    }

    private static double effectiveCutoffSquared(double maximumDistanceSquared, double suppliedCutoffSquared) {
        if (Double.isNaN(suppliedCutoffSquared)) {
            return maximumDistanceSquared;
        }
        return Math.max(0.0D, Math.min(maximumDistanceSquared, suppliedCutoffSquared));
    }

    private static double minimumHorizontalDistanceSquared(
            double x,
            double z,
            int minimumCellX,
            int maximumCellX,
            int minimumCellZ,
            int maximumCellZ
    ) {
        double minimumX = (double) minimumCellX * CELL_SIZE_BLOCKS;
        double minimumZ = (double) minimumCellZ * CELL_SIZE_BLOCKS;
        double maximumX = (double) maximumCellX * CELL_SIZE_BLOCKS + CELL_SIZE_BLOCKS;
        double maximumZ = (double) maximumCellZ * CELL_SIZE_BLOCKS + CELL_SIZE_BLOCKS;
        return square(distanceToInterval(x, minimumX, maximumX))
                + square(distanceToInterval(z, minimumZ, maximumZ));
    }

    private static double distanceToInterval(double value, double minimum, double maximum) {
        if (value < minimum) {
            return minimum - value;
        }
        if (value > maximum) {
            return value - maximum;
        }
        return 0.0D;
    }

    private static int cellCoordinate(double coordinate) {
        return (int) Math.floor(coordinate / CELL_SIZE_BLOCKS);
    }

    private static long cellKey(int cellX, int cellZ) {
        return ((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
    }

    private static double square(double value) {
        return value * value;
    }

    private record NodeVisit<T>(SpatialNode<T> node, double minimumDistanceSquared) {
    }

    private static final class CellBucket<T> {
        private final int cellX;
        private final int cellZ;
        private final List<T> values = new ArrayList<>();

        private CellBucket(int cellX, int cellZ) {
            this.cellX = cellX;
            this.cellZ = cellZ;
        }
    }

    private static final class SpatialNode<T> {
        private final int minimumCellX;
        private final int maximumCellX;
        private final int minimumCellZ;
        private final int maximumCellZ;
        private final SpatialNode<T> left;
        private final SpatialNode<T> right;
        private final CellBucket<T> bucket;

        private SpatialNode(
                int minimumCellX,
                int maximumCellX,
                int minimumCellZ,
                int maximumCellZ,
                SpatialNode<T> left,
                SpatialNode<T> right,
                CellBucket<T> bucket
        ) {
            this.minimumCellX = minimumCellX;
            this.maximumCellX = maximumCellX;
            this.minimumCellZ = minimumCellZ;
            this.maximumCellZ = maximumCellZ;
            this.left = left;
            this.right = right;
            this.bucket = bucket;
        }

        private static <T> SpatialNode<T> leaf(CellBucket<T> bucket) {
            return new SpatialNode<>(
                    bucket.cellX,
                    bucket.cellX,
                    bucket.cellZ,
                    bucket.cellZ,
                    null,
                    null,
                    bucket
            );
        }

        private static <T> SpatialNode<T> branch(SpatialNode<T> left, SpatialNode<T> right) {
            return new SpatialNode<>(
                    Math.min(left.minimumCellX, right.minimumCellX),
                    Math.max(left.maximumCellX, right.maximumCellX),
                    Math.min(left.minimumCellZ, right.minimumCellZ),
                    Math.max(left.maximumCellZ, right.maximumCellZ),
                    left,
                    right,
                    null
            );
        }

        private double minimumHorizontalDistanceSquared(double x, double z) {
            return SpatialCellIndex.minimumHorizontalDistanceSquared(
                    x,
                    z,
                    minimumCellX,
                    maximumCellX,
                    minimumCellZ,
                    maximumCellZ
            );
        }
    }
}
