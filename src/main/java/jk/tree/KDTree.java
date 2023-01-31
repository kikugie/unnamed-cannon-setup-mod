package jk.tree;
/*
 ** KDTree.java by Julian Kent
 **
 ** Licenced under the  Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License
 **
 ** Licence summary:
 ** Under this licence you are free to:
 **      Share : copy and redistribute the material in any medium or format
 **      Adapt : remix, transform, and build upon the material
 **      The licensor cannot revoke these freedoms as long as you follow the license terms.
 **
 ** Under the following terms:
 **      Attribution:
 **            You must give appropriate credit, provide a link to the license, and indicate
 **            if changes were made. You may do so in any reasonable manner, but not in any
 **            way that suggests the licensor endorses you or your use.
 **      NonCommercial:
 **            You may not use the material for commercial purposes.
 **      ShareAlike:
 **            If you remix, transform, or build upon the material, you must distribute your
 **            contributions under the same license as the original.
 **      No additional restrictions:
 **            You may not apply legal terms or technological measures that legally restrict
 **            others from doing anything the license permits.
 **
 ** See full licencing details here: http://creativecommons.org/licenses/by-nc-sa/3.0/
 **
 ** For additional licencing rights (including commercial) please contact jkflying@gmail.com
 **
 */

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;

public class KDTree<T> {
    private static final int _bucketSize = 10;

    private final int _dimensions = 3;
    private int _nodes;
    private final Node root;
    private final ArrayList<Node> nodeList = new ArrayList<>();
    private double[] mem_recycle;
    private final double[] bounds_template;
    private final ContiguousDoubleArrayList nodeMinMaxBounds;

    public KDTree() {
        nodeMinMaxBounds = new ContiguousDoubleArrayList(1024 / 8 + 2 * _dimensions);
        mem_recycle = new double[_bucketSize * _dimensions];

        bounds_template = new double[2 * _dimensions];
        Arrays.fill(bounds_template, Double.NEGATIVE_INFINITY);
        for (int i = 0, max = 2 * _dimensions; i < max; i += 2) {
            bounds_template[i] = Double.POSITIVE_INFINITY;
        }

        root = new Node();
    }

    public int size() {
        return root.entries;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public void addPoint(double[] location, T payload) {
        Node addNode = root;
        // Do a Depth First Search to find the Node where 'location' should be
        // stored
        while (addNode.pointLocations == null) {
            addNode.expandBounds(location);
            if (location[addNode.splitDim] < addNode.splitVal) {
                addNode = nodeList.get(addNode.lessIndex);
            } else {
                addNode = nodeList.get(addNode.moreIndex);
            }
        }
        addNode.expandBounds(location);

        int nodeSize = addNode.add(location, payload);

        if (nodeSize % _bucketSize == 0) {
            addNode.split();
        }
    }

    public SearchResult<T> nearestNeighbour(Vec3d searchLocation) {
        IntStack stack = new IntStack();
        PriorityQueue<T> results = new PriorityQueue<>(1, true);

        stack.push(root.index);

        boolean added = false;

        while (stack.size() > 0) {
            int nodeIndex = stack.pop();
            if (!added || results.peekPriority() > pointRectDist(nodeIndex, searchLocation)) {
                Node node = nodeList.get(nodeIndex);
                if (node.pointLocations == null) {
                    node.search(searchLocation, stack);
                } else {
                    added |= node.search(searchLocation, results) > 0;
                }
            }
        }

        double[] priorities = results.priorities;
        ArrayList<T> elements = results.elements;

        if (priorities.length == 0) {
            return null;
        }

        return new SearchResult<>(priorities[0], elements.get(0));
    }

    double pointRectDist(int offset, final Vec3d location) {
        offset *= 2 * _dimensions;
        double distance = 0;
        final double[] array = nodeMinMaxBounds.array;

        double bv = array[offset];
        double lv = location.x;
        if (bv <= lv) {
            bv = array[offset + 1];
        }
        distance += sqr(bv - lv);

        bv = array[offset + 2];
        lv = location.y;
        if (bv <= lv) {
            bv = array[offset + 3];
        }
        distance += sqr(bv - lv);

        bv = array[offset + 4];
        lv = location.z;
        if (bv <= lv) {
            bv = array[offset + 5];
        }
        distance += sqr(bv - lv);

        return distance;
    }

    double pointDist(double[] arr, Vec3d location, int index) {
        int offset = (index + 1) * _dimensions;

        return sqr(arr[offset - 1] - location.z)
                + sqr(arr[offset - 2] - location.y)
                + sqr(arr[offset - 3] - location.x);
    }

    public static class SearchResult<S> {
        public double distance;
        public S payload;

        SearchResult(double dist, S load) {
            distance = dist;
            payload = load;
        }
    }

    private class Node {

        // for accessing bounding box data
        // - if trees weren't so unbalanced might be better to use an implicit
        // heap?
        int index;

        // keep track of size of subtree
        int entries;

        // leaf
        ContiguousDoubleArrayList pointLocations;
        ArrayList<T> pointPayloads = new ArrayList<>(_bucketSize);

        // stem
        // Node less, more;
        int lessIndex, moreIndex;
        int splitDim;
        double splitVal;

        Node() {
            this(new double[_bucketSize * _dimensions]);
        }

        Node(double[] pointMemory) {
            pointLocations = new ContiguousDoubleArrayList(pointMemory);
            index = _nodes++;
            nodeList.add(this);
            nodeMinMaxBounds.add(bounds_template);
        }

        void search(Vec3d searchLocation, KDTree.IntStack stack) {
            if (axis(searchLocation, splitDim) < splitVal) {
                stack.push(moreIndex).push(lessIndex); // less will be popped
                // first
            } else {
                stack.push(lessIndex).push(moreIndex); // more will be popped
                // first
            }
        }

        // returns number of points added to results
        int search(Vec3d searchLocation, PriorityQueue<T> results) {
            int updated = 0;
            for (int j = entries; j-- > 0; ) {
                double distance = pointDist(pointLocations.array, searchLocation, j);
                if (results.peekPriority() > distance) {
                    ++updated;
                    results.addNoGrow(pointPayloads.get(j), distance);
                }
            }
            return updated;
        }

        void expandBounds(double[] location) {
            ++entries;
            int mio = index * 2 * _dimensions;
            for (int i = 0; i < _dimensions; ++i, mio += 2) {
                nodeMinMaxBounds.array[mio] = Math.min(nodeMinMaxBounds.array[mio], location[i]);
                nodeMinMaxBounds.array[mio + 1] = Math.max(nodeMinMaxBounds.array[mio + 1], location[i]);
            }
        }

        int add(double[] location, T load) {
            pointLocations.add(location);
            pointPayloads.add(load);
            return entries;
        }

        void split() {
            int offset = index * 2 * _dimensions;

            double diff = 0;
            for (int i = 0; i < _dimensions; ++i) {
                double min = nodeMinMaxBounds.array[offset];
                double max = nodeMinMaxBounds.array[offset + 1];
                if (max - min > diff) {
                    double mean = 0;
                    for (int j = 0; j < entries; ++j) {
                        mean += pointLocations.array[i + _dimensions * j];
                    }

                    mean = mean / entries;
                    double varianceSum = 0;

                    for (int j = 0; j < entries; ++j) {
                        varianceSum += sqr(mean - pointLocations.array[i + _dimensions * j]);
                    }

                    if (varianceSum > diff * entries) {
                        diff = varianceSum / entries;
                        splitVal = mean;

                        splitDim = i;
                    }
                }
                offset += 2;
            }

            if (splitVal == Double.POSITIVE_INFINITY) {
                splitVal = Double.MAX_VALUE;
            } else if (splitVal == Double.NEGATIVE_INFINITY) {
                splitVal = Double.MIN_VALUE;
            } else if (splitVal == nodeMinMaxBounds.array[index * 2 * _dimensions + 2 * splitDim + 1]) {
                splitVal = nodeMinMaxBounds.array[index * 2 * _dimensions + 2 * splitDim];
            }

            Node less = new Node(mem_recycle);
            Node more = new Node();
            lessIndex = less.index;
            moreIndex = more.index;

            double[] pointLocation = new double[_dimensions];
            for (int i = 0; i < entries; ++i) {
                System.arraycopy(pointLocations.array, i * _dimensions, pointLocation, 0, _dimensions);
                T load = pointPayloads.get(i);

                if (pointLocation[splitDim] < splitVal) {
                    less.expandBounds(pointLocation);
                    less.add(pointLocation, load);
                } else {
                    more.expandBounds(pointLocation);
                    more.add(pointLocation, load);
                }
            }

            if (less.entries * more.entries == 0) {
                nodeList.remove(moreIndex);
                nodeList.remove(lessIndex);
            } else {
                mem_recycle = pointLocations.array;
                pointLocations = null;
                pointPayloads.clear();
                pointPayloads = null;
            }
        }
    }

    private static class PriorityQueue<S> {

        ArrayList<S> elements;
        double[] priorities;
        private double minPriority;
        private int size;

        PriorityQueue(int size, boolean prefill) {
            elements = new ArrayList<>(size);
            priorities = new double[size];
            Arrays.fill(priorities, Double.POSITIVE_INFINITY);
            if (prefill) {
                minPriority = Double.POSITIVE_INFINITY;
                this.size = size;
            }
        }

        void addNoGrow(S value, double priority) {
            int index = searchFor(priority);

            System.arraycopy(priorities, index, priorities, index + 1, size - index - 1);
            priorities[index] = priority;

            if (elements.size() == priorities.length) {
                elements.remove(elements.size() - 1);
            }
            elements.add(index, value);

            minPriority = priorities[size - 1];
        }

        int searchFor(double priority) {
            int i = size - 1;
            int j = 0;
            while (i >= j) {
                int index = (i + j) >>> 1;
                if (priorities[index] < priority) {
                    j = index + 1;
                } else {
                    i = index - 1;
                }
            }
            return j;
        }

        double peekPriority() {
            return minPriority;
        }
    }

    private static class ContiguousDoubleArrayList {
        double[] array;
        int size;

        ContiguousDoubleArrayList(int size) {
            this(new double[size]);
        }

        ContiguousDoubleArrayList(double[] data) {
            array = data;
        }

        void add(double[] da) {
            if (size + da.length > array.length) {
                array = Arrays.copyOf(array, (array.length + da.length) * 2);
            }

            System.arraycopy(da, 0, array, size, da.length);
            size += da.length;
        }
    }

    private static class IntStack {
        int[] array;
        int size;

        IntStack() {
            this(64);
        }

        IntStack(int size) {
            this(new int[size]);
        }

        IntStack(int[] data) {
            array = data;
        }

        IntStack push(int i) {
            if (size >= array.length) {
                array = Arrays.copyOf(array, (array.length + 1) * 2);
            }

            array[size++] = i;
            return this;
        }

        int pop() {
            return array[--size];
        }

        int size() {
            return size;
        }
    }

    static double sqr(double d) {
        return d * d;
    }

    static double axis(Vec3d v, int axis) {
        return axis == 0 ? v.x : axis == 1 ? v.y : v.z;
    }
}