import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilterTest {

    @Test
    void testFilter() {
        Hierarchy unfiltered = new ArrayBasedHierarchy(
                new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11},
                new int[]{0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2}
        );
        Hierarchy filteredActual = HierarchyFilter.filter(unfiltered, nodeId -> nodeId % 3 != 0);
        Hierarchy filteredExpected = new ArrayBasedHierarchy(
                new int[]{1, 2, 5, 8, 10, 11},
                new int[]{0, 1, 1, 0, 1, 2}
        );
        assertEquals(filteredExpected.formatString(), filteredActual.formatString());
    }

    @Test
    void filter_whenEmptyHierarchy_returnsEmpty() {
        // Arrange
        Hierarchy hierarchy = new ArrayBasedHierarchy(new int[]{}, new int[]{});
        IntPredicate condition = id -> {
            throw new AssertionError("Predicate must not be called for empty input");
        };

        // Act
        Hierarchy actual = HierarchyFilter.filter(hierarchy, condition);

        // Assert
        assertHierarchy(actual, new int[]{}, new int[]{});
    }

    @Test
    void filter_whenSingleNodeAccepted_keepsNode() {
        // Arrange
        Hierarchy hierarchy = new ArrayBasedHierarchy(new int[]{1}, new int[]{0});

        // Act
        Hierarchy actual = HierarchyFilter.filter(hierarchy, id -> true);

        // Assert
        assertHierarchy(actual, new int[]{1}, new int[]{0});
    }

    @Test
    void filter_whenSingleNodeRejected_returnsEmpty() {
        // Arrange
        Hierarchy hierarchy = new ArrayBasedHierarchy(new int[]{1}, new int[]{0});

        // Act
        Hierarchy actual = HierarchyFilter.filter(hierarchy, id -> false);

        // Assert
        assertHierarchy(actual, new int[]{}, new int[]{});
    }

    @Test
    void filter_whenAllAccepted_keepsOrderAndDepth() {
        // Arrange
        Hierarchy hierarchy = new ArrayBasedHierarchy(
                new int[]{1, 2, 3, 4}, new int[]{0, 1, 2, 1});

        // Act
        Hierarchy actual = HierarchyFilter.filter(hierarchy, id -> true);

        // Assert
        assertHierarchy(actual, new int[]{1, 2, 3, 4}, new int[]{0, 1, 2, 1});
    }

    @Test
    void filter_whenAllRejected_returnsEmpty() {
        // Arrange
        Hierarchy hierarchy = new ArrayBasedHierarchy(
                new int[]{1, 2, 3, 4}, new int[]{0, 1, 0, 1});

        // Act
        Hierarchy actual = HierarchyFilter.filter(hierarchy, id -> false);

        // Assert
        assertHierarchy(actual, new int[]{}, new int[]{});
    }

    @Test
    void filter_whenRootRejected_keepsOtherTrees() {
        // Arrange
        Hierarchy hierarchy = new ArrayBasedHierarchy(
                new int[]{1, 2, 3, 4, 5}, new int[]{0, 1, 2, 0, 1});

        // Act
        Hierarchy actual = HierarchyFilter.filter(hierarchy, id -> id != 1);

        // Assert
        assertHierarchy(actual, new int[]{4, 5}, new int[]{0, 1});
    }

    @Test
    void filter_whenParentRejected_excludesDescendants() {
        // Arrange
        Hierarchy hierarchy = new ArrayBasedHierarchy(
                new int[]{1, 2, 3, 4, 5, 6}, new int[]{0, 1, 2, 3, 1, 2});

        // Act
        Hierarchy actual = HierarchyFilter.filter(hierarchy, id -> id != 2);

        // Assert
        assertHierarchy(actual, new int[]{1, 5, 6}, new int[]{0, 1, 2});
    }

    @Test
    void filter_whenLeafRejected_keepsSiblings() {
        // Arrange
        Hierarchy hierarchy = new ArrayBasedHierarchy(
                new int[]{1, 2, 3}, new int[]{0, 1, 1});

        // Act
        Hierarchy actual = HierarchyFilter.filter(hierarchy, id -> id != 2);

        // Assert
        assertHierarchy(actual, new int[]{1, 3}, new int[]{0, 1});
    }

    @Test
    void filter_whenParentRejected_skipsDescendantChecks() {
        // Arrange
        Hierarchy hierarchy = new ArrayBasedHierarchy(
                new int[]{1, 2, 3, 4, 5}, new int[]{0, 1, 2, 3, 1});
        List<Integer> visitedNodeIds = new ArrayList<>();
        IntPredicate condition = id -> {
            visitedNodeIds.add(id);
            return id != 2;
        };

        // Act
        HierarchyFilter.filter(hierarchy, condition);

        // Assert
        assertEquals(List.of(1, 2, 5), visitedNodeIds);
    }

    @Test
    void filter_whenNodeRejected_keepsInputUnchanged() {
        // Arrange
        Hierarchy hierarchy = new ArrayBasedHierarchy(
                new int[]{1, 2, 3}, new int[]{0, 1, 1});

        // Act
        HierarchyFilter.filter(hierarchy, id -> id != 2);

        // Assert
        assertHierarchy(hierarchy, new int[]{1, 2, 3}, new int[]{0, 1, 1});
    }

    @Test
    void filter_whenNullHierarchy_throwsNullPointerException() {
        // Arrange
        IntPredicate condition = id -> true;

        // Act & Assert
        assertThrows(NullPointerException.class,
                () -> HierarchyFilter.filter(null, condition));
    }

    @Test
    void filter_whenNullPredicate_throwsNullPointerException() {
        // Arrange
        Hierarchy hierarchy = new ArrayBasedHierarchy(new int[]{1}, new int[]{0});

        // Act & Assert
        assertThrows(NullPointerException.class,
                () -> HierarchyFilter.filter(hierarchy, null));
    }

    @Test
    void filter_whenConsecutiveSiblingsRejected_keepsNextBranch() {
        // Arrange
        Hierarchy hierarchy = new ArrayBasedHierarchy(
                new int[]{1, 2, 3, 4, 5, 6, 7},
                new int[]{0, 1, 2, 1, 2, 1, 2});

        // Act
        Hierarchy actual = HierarchyFilter.filter(hierarchy, id -> id != 2 && id != 4);

        // Assert
        assertHierarchy(actual, new int[]{1, 6, 7}, new int[]{0, 1, 2});
    }

    @Test
    void filter_whenDeepHierarchy_keepsNodesBeforeRejection() {
        // Arrange
        int size = 100_000;
        int rejectedId = size / 2;
        int[] nodeIds = new int[size];
        int[] depths = new int[size];
        for (int i = 0; i < size; i++) {
            nodeIds[i] = i;
            depths[i] = i;
        }
        Hierarchy hierarchy = new ArrayBasedHierarchy(nodeIds, depths);

        // Act
        Hierarchy actual = HierarchyFilter.filter(hierarchy, id -> id != rejectedId);

        // Assert
        assertEquals(rejectedId, actual.size());
        for (int i = 0; i < actual.size(); i++) {
            assertEquals(i, actual.nodeId(i));
            assertEquals(i, actual.depth(i));
        }
    }

    private static void assertHierarchy(
            Hierarchy actual, int[] expectedNodeIds, int[] expectedDepths) {
        assertEquals(expectedNodeIds.length, actual.size());
        for (int i = 0; i < actual.size(); i++) {
            assertEquals(expectedNodeIds[i], actual.nodeId(i), "Node ID at index " + i);
            assertEquals(expectedDepths[i], actual.depth(i), "Depth at index " + i);
        }
    }
}
