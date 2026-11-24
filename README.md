# Coding Assessment

## Tasks

- [Hierarchy filter](src/main/java/Hierarchy.java) and [tests](src/test/java/FilterTest.java)
- [SimpleCache code review](SimpleCache.md)

## Approach

The input is already in DFS order.
I check each node and keep it if it passes the filter condition.
If it fails, skipSubtree skips the following nodes until the depth is equal to or smaller than the rejected node's depth.
This keeps the original order and depths without building a tree or using recursion.

Time complexity is O(n), where n is the number of nodes, assuming constant-time node access and condition checks.
Space complexity is O(n) for the two result arrays and their trimmed copies.

I used primitive arrays to avoid boxing and resizing, although some allocated space may be unused when many nodes are rejected.

## Assumptions

- The input follows the documented hierarchy rules, with unique IDs and matching array lengths. I did not add validation for malformed hierarchies.
- The filter condition does not rely on side effects or being called for every node. Descendants of rejected nodes are skipped.
- Null arguments throw NullPointerException.
- The original hierarchy is not modified. ArrayBasedHierarchy keeps references to its input arrays, so callers must not modify them during filtering.
- All filtering state is local to each call. Concurrent calls are safe if the input is safely published and not modified, and any shared filter condition is thread-safe. No extra locks are needed.

## Tests

I kept the original test and added more test cases (15 tests in total).

- Empty and single-node hierarchies
- All nodes accepted or rejected
- Rejected roots, parents, leaves and consecutive siblings
- Filter condition call sequence
- Original input unchanged
- Null arguments
- Deep hierarchy with 100,000 nodes

## Running

Requires Java 21 and Maven. Run from the repository root:

```sh
mvn clean test
```

GitHub Actions also runs the tests with Java 21.
