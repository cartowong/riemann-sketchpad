# Riemann Sphere Geometry Engine

Specification for the geometry computation layer. This module is pure math — no UI framework imports. All classes live in the `geometry/` package.

The geometry uses the **Riemann sphere** model of elliptic geometry: the unit sphere `{(x, y, z) in R^3 : x^2 + y^2 + z^2 = 1}` in 3D Euclidean space. Geodesics are great circles (intersections of planes through the origin with the sphere).

---

## 1. Core Interfaces

### 1.1 GeometricObject

```
sealed interface GeometricObject {
    id: Int                              // immutable, assigned in construction order, starting at 0
    parentIds: List<Int>                 // IDs of parent objects (empty for free points)
    isDefined: Boolean                   // false when geometrically invalid (e.g., antipodal points)
    recompute(objects: Map<Int, Object>) // recompute position/state from parent objects
}
```

Every concrete geometric type implements this interface. The sealed constraint means the set of types is closed — adding a new type requires updating all exhaustive `when` expressions in serializers, the object manager, and the sidebar.

**Note:** There is no `distanceFrom` method. Hit-testing is performed in screen-pixel space by the UI layer, not by the geometry objects.

### 1.2 PointLike

```
interface PointLike {
    x: Double    // x-coordinate on the unit sphere
    y: Double    // y-coordinate on the unit sphere
    z: Double    // z-coordinate on the unit sphere
}
```

All point types implement both `GeometricObject` and `PointLike`. Coordinates satisfy `x^2 + y^2 + z^2 = 1` (on the unit sphere). Non-point code reads coordinates through this interface without knowing the concrete point type.

### 1.3 GeodesicLike

```
enum GeodesicType { LINE, SEGMENT }

interface GeodesicLike {
    point1Id: Int              // first defining point ID
    point2Id: Int              // second defining point ID
    geodesicType: GeodesicType
    normalX: Double            // x-component of the great circle's plane normal
    normalY: Double            // y-component of the great circle's plane normal
    normalZ: Double            // z-component of the great circle's plane normal
    x1, y1, z1: Double        // first defining point coordinates
    x2, y2, z2: Double        // second defining point coordinates
}
```

All geodesic objects (Line, Segment, PerpendicularLine) implement both `GeometricObject` and `GeodesicLike`. The normal vector defines the plane through the origin whose intersection with the unit sphere is the great circle.

**Geodesic computation:** Given two non-antipodal points `P1` and `P2` on the unit sphere, the great circle's plane normal is:
```
n = normalize(P1 × P2)
```
If `P1` and `P2` are antipodal (`P1 + P2 ≈ 0`), the great circle is undefined (infinitely many great circles pass through antipodal points).

### 1.4 Geodesic Parameterization

Geodesics are parameterized by angle along the great circle:

- **Line (full great circle)**: parameter `t` in `[0, 2π)` traverses the entire circle. `P(t) = P1 * cos(t) + (n × P1) * sin(t)` where `n` is the plane normal.
- **Segment**: the shorter arc between `P1` and `P2`. Parameter `t` in `[0, 1]` maps linearly from `P1` to `P2` via spherical interpolation (slerp).

No ideal points (the sphere has no boundary). No Ray type (removed — a ray on the sphere would be a full great circle minus a point, which is degenerate).

---

## 2. Object Types

### 2.1 Free Points

**FreePoint**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `x` | Double (mutable) | x-coordinate on unit sphere |
| `y` | Double (mutable) | y-coordinate on unit sphere |
| `z` | Double (mutable) | z-coordinate on unit sphere |

- Parents: none.
- Always defined.
- `recompute()`: no-op.
- Coordinates are normalized to the unit sphere on construction and during drag.

**FreePointOnLine**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `lineId` | Int | Parent GeodesicLike object ID (must be a Line) |
| `t` | Double (mutable) | Angular parameter in `[0, 2π)` along the great circle |

- Parents: `[lineId]`.
- `recompute()`: computes position on the great circle at angular parameter `t`. `P(t) = P1 * cos(t) + (n × P1) * sin(t)`. Undefined if parent is undefined.
- `updateParameterFromPosition(px, py, pz)`: computes the nearest `t` on the parent's great circle from the given position.

**FreePointOnSegment**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `segmentId` | Int | Parent Segment object ID |
| `t` | Double (mutable) | Parameter in `[0, 1]`, slerp from point1 to point2 |

- Parents: `[segmentId]`.
- `recompute()`: spherical linear interpolation between the segment's endpoints at parameter `t`. Undefined if parent is undefined.
- `updateParameterFromPosition(px, py, pz)`: computes the nearest `t` on the segment arc.

**FreePointOnCircle**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `circleId` | Int | Parent Circle object ID |
| `theta` | Double (mutable) | Angular parameter on the small circle |

- Parents: `[circleId]`.
- `recompute()`: position on the small circle at angle `theta`. Computed using a local orthonormal frame at the circle's center. Undefined if parent circle is undefined.
- `updateParameterFromPosition(px, py, pz)`: computes the nearest `theta` on the circle.

### 2.2 Derived Points

**Midpoint**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `point1Id` | Int | First parent point |
| `point2Id` | Int | Second parent point |

- Parents: `[point1Id, point2Id]`.
- `recompute()`: `M = normalize(P1 + P2)`. Undefined if `P1 + P2 ≈ 0` (antipodal points).
- Not movable.

**ReflectionPoint**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `pointId` | Int | Point to reflect |
| `geodesicId` | Int | GeodesicLike object to reflect across |

- Parents: `[pointId, geodesicId]`.
- `recompute()`: reflects the point through the great circle's plane. `P' = P - 2(P · n)n` where `n` is the geodesic's plane normal. Always well-defined (no degenerate cases). Undefined only if parents are undefined.
- Not movable.

### 2.3 Intersection Points

All intersection types implement `PointLike`. They become undefined when the intersection does not exist or when parent objects are undefined.

On the sphere, two distinct great circles always intersect at exactly two antipodal points. A great circle and a small circle intersect at 0 or 2 points. Two small circles intersect at 0 or 2 points.

**GeodesicGeodesicIntersection**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `geodesic1Id` | Int | First GeodesicLike parent |
| `geodesic2Id` | Int | Second GeodesicLike parent |
| `sign` | Int | `+1` or `-1`, set at construction time |

- Parents: `[geodesic1Id, geodesic2Id]`.
- `recompute()`:
  1. Compute the intersection axis: `v = n1 × n2` where `n1`, `n2` are the plane normals.
  2. If `|v| ≈ 0`, the great circles are identical or nearly so — undefined.
  3. Normalize: the two intersection points are `+normalize(v)` and `-normalize(v)`.
  4. Select by `sign`: `+1` or `-1`.
  5. For segments: check if the selected point lies on both arcs. If not, undefined.
- **Note:** Unlike Poincare Sketchpad, the `sign` field is required for geodesic-geodesic intersections because two distinct great circles always meet at two antipodal points.

**GeodesicCircleIntersection**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `geodesicId` | Int | GeodesicLike parent |
| `circleId` | Int | Circle parent |
| `sign` | Int | `+1` or `-1`, set at construction time |

- Parents: `[geodesicId, circleId]`.
- `recompute()`:
  1. The great circle lies in the plane `n · P = 0`. The small circle lies in the plane `c · P = cos(r)` where `c` is the spherical center and `r` is the spherical radius.
  2. Intersect the two planes with the sphere to find 0 or 2 candidate points.
  3. For segments: filter candidates to those on the arc.
  4. Select by `sign`.
  5. Undefined if no valid candidates.

**CircleCircleIntersection**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `circle1Id` | Int | First Circle parent |
| `circle2Id` | Int | Second Circle parent |
| `sign` | Int | `+1` or `-1`, set at construction time |

- Parents: `[circle1Id, circle2Id]`.
- `recompute()`:
  1. Each small circle lies in a plane: `c1 · P = cos(r1)` and `c2 · P = cos(r2)`.
  2. Intersect the two planes with the sphere to find 0 or 2 candidate points.
  3. Select by `sign`.
  4. Undefined if no valid candidates.

### 2.4 Geodesic Objects

All geodesic objects implement both `GeometricObject` and `GeodesicLike`.

**Line**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `point1Id` | Int | First defining point |
| `point2Id` | Int | Second defining point |

- `geodesicType`: `LINE` (full great circle).
- `recompute()`: copies coordinates from both parent points. Computes plane normal `n = normalize(P1 × P2)`. Undefined if either parent is undefined or if both points are antipodal.

**Segment**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `point1Id` | Int | First endpoint |
| `point2Id` | Int | Second endpoint |

- `geodesicType`: `SEGMENT` (shorter arc between endpoints).
- `recompute()`: same normal computation as Line. Undefined if points are antipodal (shorter arc is ambiguous). The arc length is `arccos(P1 · P2)`, which is always in `(0, π)` for non-coincident, non-antipodal points.

**PerpendicularLine**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `pointId` | Int | Point the line passes through |
| `geodesicId` | Int | GeodesicLike object to be perpendicular to |

- `geodesicType`: `LINE`.
- `recompute()`:
  1. Find the foot of the perpendicular from the point to the parent geodesic. The foot is: `F = normalize(P - (P · n) * n)` where `n` is the parent geodesic's plane normal.
  2. If `P` is a pole of the geodesic (i.e., `P ≈ ±n`), the perpendicular is undefined (`isDefined = false`).
  3. The perpendicular great circle passes through `P` and `F`. Its normal is `normalize(P × F)`.
  4. Store `x1,y1,z1` = P and `x2,y2,z2` = F. If P lies on the geodesic (F = P), use a distinct second point along the perpendicular direction.
- Undefined if parents are undefined or if P is a pole.

### 2.5 Circle

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `centerId` | Int | Center point (spherical center) |
| `radiusPointId` | Int | Point on the circle |

- Parents: `[centerId, radiusPointId]`.
- Computed properties:
  - `sphericalRadius`: the spherical distance from center to radius point, `arccos(C · R)`. Always in `(0, π)`.
  - `normalX`, `normalY`, `normalZ`: the center point (which is the pole/normal of the small circle's plane).
  - `cosRadius`: `cos(sphericalRadius) = C · R`. The small circle is the intersection of the plane `C · P = cosRadius` with the unit sphere.
- Does not implement `GeodesicLike` or `PointLike`.
- `recompute()`: computes `sphericalRadius = arccos(C · R)`. Undefined if either parent is undefined or if the center and radius point are antipodal (`C · R ≈ -1`).

### 2.6 Angle

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `pointAId` | Int | First ray point |
| `vertexId` | Int | Vertex point |
| `pointCId` | Int | Second ray point |

- Parents: `[pointAId, vertexId, pointCId]`.
- Computed properties: `vertexX`, `vertexY`, `vertexZ`, `startAngleRadians`, `sweepAngleRadians`.
- Does not implement `GeodesicLike` or `PointLike`.
- `recompute()`:
  The angle between two geodesics on the sphere equals the dihedral angle between their planes, which equals the angle between the tangent vectors at the vertex.
  ```
  // Tangent of geodesic VA at vertex V:
  //   tA = normalize(A - (A · V) * V)   (component of A perpendicular to V)
  // Tangent of geodesic VC at vertex V:
  //   tC = normalize(C - (C · V) * V)
  //
  // Orientation: use the outward normal V at the vertex
  // Start angle and sweep are computed in the tangent plane at V,
  // with orientation defined by the outward normal (counterclockwise as seen from outside)
  ```
- `measureDegrees()`: returns the angle in degrees (positive value).

### 2.7 Region

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Object ID |
| `pointIds` | List\<Int\> | Ordered list of vertex point IDs |

- Parents: `pointIds` (all vertex points).
- Computed property: `vertices: List<Triple<Double, Double, Double>>` — resolved coordinates.
- Does not implement `GeodesicLike` or `PointLike`.
- `recompute()`: resolves all point IDs to `(x, y, z)` triples. Undefined if any parent point is undefined.
- `measureArea()`: spherical excess formula. For a spherical polygon with interior angles `α_1, ..., α_n`:
  ```
  area = (sum of interior angles) - (n - 2) * π
  ```
  Each interior angle is computed from the tangent vectors of adjacent geodesic edges at each vertex, using the outward normal for consistent orientation.

---

## 3. Utility Functions

### 3.1 SphericalUtils

Core spherical geometry functions operating on the unit sphere.

| Function | Description |
|----------|-------------|
| `sphericalDistance(P1, P2)` | `arccos(P1 · P2)`, clamped to `[0, π]` |
| `greatCircleNormal(P1, P2)` | `normalize(P1 × P2)` — plane normal of the great circle through P1 and P2 |
| `tangentVector(P, Q)` | `normalize(Q - (Q · P) * P)` — unit tangent at P toward Q along the great circle |
| `slerp(P1, P2, t)` | Spherical linear interpolation: `P1 * sin((1-t)*Ω)/sin(Ω) + P2 * sin(t*Ω)/sin(Ω)` where `Ω = arccos(P1 · P2)` |
| `reflectThroughGreatCircle(P, n)` | `P - 2(P · n) * n` — reflection through the great circle with normal `n` |
| `midpoint(P1, P2)` | `normalize(P1 + P2)` — spherical midpoint (undefined for antipodal points) |
| `footOfPerpendicular(P, n)` | `normalize(P - (P · n) * n)` — nearest point on the great circle with normal `n` to point P |
| `isAntipodal(P1, P2)` | `P1 · P2 < -1 + epsilon` |
| `normalizeToSphere(x, y, z)` | Normalize `(x, y, z)` to unit length |
| `projectToScreen(P, viewMatrix)` | Orthographic projection: apply view rotation, return `(sx, sy)` and depth `sz` |
| `unprojectFromScreen(sx, sy, viewMatrix)` | Inverse: given screen `(sx, sy)`, compute point on the visible hemisphere. `sz = sqrt(1 - sx² - sy²)` in rotated frame, then apply inverse view rotation |
| `isOnFrontHemisphere(P, viewMatrix)` | `sz > 0` after applying view rotation (true if point faces the camera) |
| `ellipseProjectionParams(normal, viewMatrix)` | Given a great circle or small circle's plane normal and the view matrix, compute the projected ellipse parameters: `(centerX, centerY, semiMajor, semiMinor, rotationAngle)` |

### 3.2 IntersectionUtils

Epsilon constant: `1e-10`.

Intersection algorithms operate on planes and the unit sphere.

**greatCircleGreatCircleIntersection(n1, n2)**:
- Intersection axis: `v = n1 × n2`.
- If `|v| < epsilon`, circles are coincident — no discrete intersection.
- Otherwise, the two intersection points are `±normalize(v)`.
- Returns 0 or 2 candidate points.

**greatCircleSmallCircleIntersection(n, center, cosRadius)**:
- Great circle plane: `n · P = 0`. Small circle plane: `center · P = cosRadius`.
- Intersect both planes with the sphere. This reduces to finding points on the unit sphere satisfying both linear constraints.
- Returns 0 or 2 candidate points.

**smallCircleSmallCircleIntersection(c1, cosR1, c2, cosR2)**:
- Two plane constraints: `c1 · P = cosR1` and `c2 · P = cosR2`.
- Intersect with the sphere.
- Returns 0 or 2 candidate points.

**isOnSegmentArc(point, P1, P2, normal)**:
- Checks whether a candidate point lies on the shorter arc from P1 to P2.
- Uses the condition that the point is on the same side as the arc midpoint relative to the chord.

**assignSign(candidates, refPoint, refDirection)**:
- Sign disambiguation for selecting between two candidate intersection points.
- Uses dot product along a reference direction to assign `+1` or `-1`.

### 3.3 MeasurementUtils

| Function | Input | Formula |
|----------|-------|---------|
| `segmentLength(seg)` | Segment | `sphericalDistance(P1, P2) = arccos(P1 · P2)` |
| `circleCircumference(circle)` | Circle | `2π sin(r)` where `r` is the spherical radius |
| `angleDegrees(angle)` | Angle | Angle in degrees from tangent vectors at vertex |
| `regionArea(region)` | Region | `(sum of interior angles) - (n - 2) * π` (spherical excess) |

`measure(obj)` dispatches by type and returns `null` for non-measurable types (points, lines).

`measureLabel(obj)` returns a formatted string: `"%.2f"` for lengths/circumferences/areas, `"%.1f\u00B0"` for angles.

---

## 4. Dependency Graph

- Every object's ID is assigned in construction order, starting at 0.
- A parent's ID is always lower than its child's ID.
- **Update propagation**: iterate objects from lowest to highest ID. Each object with a dirty parent calls `recompute()` once. The ID ordering guarantees a valid topological sort.
- **Undefined cascading**: when an object becomes undefined (e.g., parent points become antipodal), all descendants that depend on it also become undefined via their `recompute()` checks.

---

## 5. Camera and Viewport

The viewport is defined by a camera orientation (azimuth, elevation) that controls the orthographic projection from the 3D unit sphere to the 2D canvas disk.

### 5.1 Camera State

| Parameter | Range | Default | Description |
|-----------|-------|---------|-------------|
| `azimuth` | `[-180°, 180°]` | `0°` | Horizontal rotation around the Y-axis |
| `elevation` | `[-90°, 90°]` | `0°` | Vertical rotation around the camera's local X-axis |

### 5.2 View Matrix

The view matrix `V` is a 3x3 rotation matrix computed from azimuth `θ` and elevation `φ`:

```
V = Rx(φ) * Ry(-θ)
```

Where `Ry(-θ)` rotates around the Y-axis by `-θ` (azimuth), and `Rx(φ)` rotates around the X-axis by `φ` (elevation). The camera looks along the `-Z` direction in the rotated frame.

### 5.3 Orthographic Projection

For a point `P = (x, y, z)` on the unit sphere:

1. Apply the view rotation: `P' = V * P`, yielding `(px, py, pz)`.
2. Screen coordinates: `screenX = px`, `screenY = py`.
3. Depth: `depth = pz`. Points with `pz > 0` are on the front (visible) hemisphere.

The display transform then scales and translates: `displayX = diskCenterX + displayScale * screenX`, `displayY = diskCenterY - displayScale * screenY` (Y-flipped for screen coordinates).

### 5.4 Inverse Projection

Given screen coordinates `(sx, sy)` (in model space, before display transform):

1. If `sx² + sy² > 1`, the click is outside the sphere — ignored.
2. `sz = sqrt(1 - sx² - sy²)` — depth on the front hemisphere.
3. The model-space point is: `P = V^T * (sx, sy, sz)` — apply inverse view rotation.

### 5.5 Named Perspectives

Six reference perspectives (not snap targets — the slider moves continuously):

| Name | Azimuth | Elevation | Camera looks along |
|------|---------|-----------|-------------------|
| Front | 0° | 0° | -Z |
| Back | 180° | 0° | +Z |
| Right | 90° | 0° | -X |
| Left | -90° | 0° | +X |
| Top | 0° | 90° | -Y |
| Bottom | 0° | -90° | +Y |

### 5.6 Space+Drag

- **Horizontal drag**: changes azimuth (positive drag-right increases azimuth).
- **Vertical drag**: changes elevation (positive drag-up increases elevation).
- Sliders and Space+Drag control the same azimuth/elevation state and stay in sync.

---

## 6. Ellipse Projection

### 6.1 Great Circle Projection

Under orthographic projection, a great circle (plane through origin intersecting the unit sphere) projects to an ellipse on screen. The ellipse parameters are computed from the plane normal `n` and the view matrix `V`:

1. Apply view rotation to the normal: `n' = V * n`.
2. The projected ellipse has:
   - **Semi-major axis**: always `1` (the great circle has radius 1, and orthographic projection preserves the widest dimension).
   - **Semi-minor axis**: `|n'z|` — the component of the rotated normal along the viewing direction. When `n'z = 0`, the great circle is seen edge-on (projects to a diameter). When `|n'z| = 1`, it projects to the full boundary circle.
   - **Rotation angle**: `atan2(n'x, n'y)` — the orientation of the projected ellipse on screen.
3. **Degenerate threshold**: when `semiMinor < 0.01`, render as a straight line segment (diameter) instead of an ellipse.

### 6.2 Small Circle Projection

A small circle (plane not through origin intersecting the unit sphere) projects to an ellipse with parameters dependent on the plane's offset from the origin:

1. The circle lies in the plane `c · P = cos(r)` where `c` is the spherical center and `r` is the spherical radius.
2. The Euclidean radius of the circle in 3D is `sin(r)`.
3. The Euclidean center of the circle in 3D is `c * cos(r)`.
4. Apply view rotation to get projected ellipse parameters. The semi-axes and center shift depend on the tilt of the circle's plane relative to the view direction.

### 6.3 Split Rendering at Silhouette

When a great circle or small circle crosses the silhouette (the sphere's equator as seen from the camera), it must be rendered in two segments: front and back. The front segment is rendered at full opacity; the back segment at reduced opacity (0.3).

The silhouette crossing points are where `pz = 0` in the rotated frame. These are computed analytically from the circle's parameterization and the view matrix.

---

## 7. Type Summary

| Type | Implements | Parents | Movable | Measurable |
|------|-----------|---------|---------|------------|
| FreePoint | PointLike | none | yes | no |
| FreePointOnLine | PointLike | GeodesicLike | yes (constrained) | no |
| FreePointOnSegment | PointLike | GeodesicLike | yes (constrained) | no |
| FreePointOnCircle | PointLike | Circle | yes (constrained) | no |
| Midpoint | PointLike | Point, Point | no | no |
| ReflectionPoint | PointLike | Point, GeodesicLike | no | no |
| GeodesicGeodesicIntersection | PointLike | GeodesicLike, GeodesicLike | no | no |
| GeodesicCircleIntersection | PointLike | GeodesicLike, Circle | no | no |
| CircleCircleIntersection | PointLike | Circle, Circle | no | no |
| Line | GeodesicLike | Point, Point | no | no |
| Segment | GeodesicLike | Point, Point | no | yes (length) |
| PerpendicularLine | GeodesicLike | Point, GeodesicLike | no | no |
| Circle | — | Point, Point | no | yes (circumference) |
| Angle | — | Point, Point, Point | no | yes (degrees) |
| Region | — | Point list | no | yes (area) |

---

## 8. File Layout

```
geometry/
  GeometricObject.kt    # GeometricObject sealed interface
  Point.kt              # PointLike interface, FreePoint, FreePointOn*, Midpoint, ReflectionPoint
  Geodesic.kt           # GeodesicType enum, GeodesicLike interface, Line, Segment, PerpendicularLine
  Circle.kt             # Circle (small circle on the sphere)
  Angle.kt              # Angle (tangent-vector-based)
  Region.kt             # Region (spherical excess area)
  Intersection.kt       # GeodesicGeodesicIntersection, GeodesicCircleIntersection, CircleCircleIntersection
  IntersectionUtils.kt  # IntersectionUtils object (plane-sphere intersection algorithms)
  SphericalUtils.kt     # SphericalUtils object (spherical distance, slerp, projection, reflection, etc.)
  MeasurementUtils.kt   # MeasurementUtils object (measurement dispatch)
```
