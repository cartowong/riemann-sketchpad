# Riemann Sketchpad — Handoff Document

**Date:** 2026-11-15
**Branch:** `main`

## Current State

- **Build:** `./gradlew test` passes (all tests green). `build.gradle.kts` extracts version from `Main.kt`.
- **Code:** Both checkpoints complete. Full application (`geometry/`, `model/`, `persistence/`, `ui/`, `export/`) implemented.
- **Git:** Repository initialized. `.claude/` is gitignored (except `.claude/skills/`).
- **Spec:** `docs/SPEC.md` and `docs/GEOMETRY.md` describe the full application design.
- **CI:** GitHub Actions `ci.yml` for running tests on PRs. `release.yml` builds macOS (.dmg) and Windows (.msi) installers on `v*` tag push.

## Planned Package Structure

```
src/main/kotlin/riemannsketchpad/
  Main.kt                          # APP_NAME, APP_VERSION, entry point
  geometry/                        # Pure math (no JavaFX)
    GeometricObject.kt             # Sealed interface
    SphericalUtils.kt              # Core spherical math
    Point.kt                       # PointLike, FreePoint, FreePointOn*, Midpoint, ReflectionPoint
    Geodesic.kt                    # GeodesicLike, Line, Segment, PerpendicularLine
    Circle.kt                      # Small circle on the sphere
    Intersection.kt                # GeodesicGeodesic, GeodesicCircle, CircleCircle intersections
    IntersectionUtils.kt           # Plane-sphere intersection algorithms
    Angle.kt                       # Tangent-based angle computation
    Region.kt                      # Spherical excess area
    MeasurementUtils.kt            # Spherical measurement formulas
  model/
    ObjectMetadata.kt              # Per-object mutable state (incl. style fields)
    GeometricObjectManager.kt      # Object map, add/remove/move/propagate/snapshot
    StyleSettings.kt               # Application-level default styles per object category
    StyleUtils.kt                  # Shared color/size/thickness maps
    UndoManager.kt                 # Snapshot-based undo/redo
    ConstructionRecorder.kt        # Recording lifecycle
    ConstructionApplicator.kt      # Apply constructions with ID remapping
  persistence/
    DiagramSerializer.kt           # JSON serialization with model:"riemann" validation
    ConstructionSerializer.kt      # Construction save/load
  ui/
    Tool.kt, Toolbar.kt            # Tool interface and toolbar
    ToolUtils.kt                   # resolveOrCreatePoint, constructIntersection
    PointTool.kt                   # Point creation tool
    SelectionTool.kt               # Selection and drag tool
    GeodesicTool.kt                # Line/Segment tool with elliptical arc previews
    CircleTool.kt                  # Circle tool
    AngleTool.kt                   # Three-click angle tool
    RegionTool.kt                  # Multi-click polygon tool
    LabelTool.kt                   # Label assignment and drag tool
    SphereCanvasPane.kt            # Main canvas (sphere rendering, viewport, apply mode)
    GeometryRenderer.kt            # 5-pass renderer with front/back-face opacity
    HitTester.kt                   # Screen-pixel hit-testing with intersection cache
    Sidebar.kt                     # Object list, constructions table, measurements table
    SketchpadApp.kt                # Full app shell with menus
  export/
    ExportDialog.kt                # Canvas/Custom export dialog
    ExportRenderer.kt              # PNG (snapshot) and SVG (manual XML) export
    HeadlessExportApp.kt           # CLI --export mode

src/test/kotlin/riemannsketchpad/
  geometry/
    SphericalUtilsTest.kt
    GeodesicTest.kt
    CircleTest.kt
    IntersectionTest.kt
    MeasurementUtilsTest.kt
  model/
    GeometricObjectManagerTest.kt
    UndoManagerTest.kt
  persistence/
    DiagramSerializerTest.kt
    ConstructionSerializerTest.kt
```

## Implementation Checkpoints

The implementation is split into two checkpoints at a clean architectural boundary: the `geometry/`, `model/`, and `persistence/` packages are pure Kotlin (no JavaFX), while `ui/` and `export/` depend on JavaFX.

### Checkpoint 1: Core Engine (geometry + model + persistence)

**Status:** Complete — all 9 test files pass (`./gradlew test`)

**Scope — source files:**
- `geometry/GeometricObject.kt` — sealed interface, PointLike, GeodesicLike, GeodesicType
- `geometry/SphericalUtils.kt` — spherical distance, slerp, projection, reflection, view matrix
- `geometry/Point.kt` — FreePoint, FreePointOnLine, FreePointOnSegment, FreePointOnCircle, Midpoint, ReflectionPoint
- `geometry/Geodesic.kt` — Line, Segment, PerpendicularLine
- `geometry/Circle.kt` — small circle on the sphere
- `geometry/Intersection.kt` — GeodesicGeodesicIntersection, GeodesicCircleIntersection, CircleCircleIntersection
- `geometry/IntersectionUtils.kt` — plane-sphere intersection algorithms
- `geometry/Angle.kt` — tangent-based angle computation
- `geometry/Region.kt` — spherical excess area
- `geometry/MeasurementUtils.kt` — measurement dispatch and formatting
- `model/ObjectMetadata.kt` — per-object mutable state (visibility, label, style fields)
- `model/GeometricObjectManager.kt` — object map, add/remove/move/propagate/snapshot/restore
- `model/StyleSettings.kt` — application-level default styles per object category
- `model/StyleUtils.kt` — shared color/size/thickness mappings
- `model/UndoManager.kt` — snapshot-based undo/redo
- `model/ConstructionRecorder.kt` — recording lifecycle (start/save/cancel)
- `model/ConstructionApplicator.kt` — apply constructions with ID remapping and preview
- `persistence/DiagramSerializer.kt` — JSON serialization with model:"riemann" validation
- `persistence/ConstructionSerializer.kt` — construction save/load, style stripping

**Scope — test files:**
- `geometry/SphericalUtilsTest.kt`
- `geometry/GeodesicTest.kt`
- `geometry/CircleTest.kt`
- `geometry/IntersectionTest.kt`
- `geometry/MeasurementUtilsTest.kt`
- `model/GeometricObjectManagerTest.kt`
- `model/UndoManagerTest.kt`
- `persistence/DiagramSerializerTest.kt`
- `persistence/ConstructionSerializerTest.kt`

**Verification:** `./gradlew test` — all unit tests pass. No JavaFX runtime needed.

**Commit message convention:** `checkpoint-1: <description>`

### Checkpoint 2: UI + Export (full application)

**Status:** Complete

**Scope — source files:**
- `ui/Tool.kt` — Tool interface with default no-op methods
- `ui/Toolbar.kt` — 8-button toolbar with ToggleGroup
- `ui/ToolUtils.kt` — resolveOrCreatePoint, constructIntersection helpers
- `ui/PointTool.kt` — point creation (free, on-object, intersection)
- `ui/SelectionTool.kt` — selection, modifier-click, drag
- `ui/GeodesicTool.kt` — Line/Segment two-click tool with preview
- `ui/CircleTool.kt` — Circle two-click tool
- `ui/AngleTool.kt` — Angle three-click tool
- `ui/RegionTool.kt` — Region multi-click tool
- `ui/LabelTool.kt` — label assign/toggle/drag
- `ui/SphereCanvasPane.kt` — canvas pane, sphere disk, mouse dispatch, apply mode
- `ui/GeometryRenderer.kt` — 5-pass renderer with front/back-face, elliptical arcs
- `ui/HitTester.kt` — screen-pixel hit-testing with intersection cache
- `ui/Sidebar.kt` — object list, constructions table, measurements table
- `ui/SketchpadApp.kt` — Application subclass, menus, three-column layout
- `export/ExportDialog.kt` — canvas/custom export dialog with crop overlay
- `export/ExportRenderer.kt` — PNG (snapshot) and SVG (manual XML) export
- `export/HeadlessExportApp.kt` — CLI `--export` mode
- `Main.kt` — wired-up entry point (dispatches to SketchpadApp or HeadlessExportApp)

**Verification:** `./gradlew run` — the full application launches. All tools, menus, sidebar, export, and constructions work.

**Commit message convention:** `checkpoint-2: <description>`

---

## Architecture Notes

- **Sister project to PoincareSketchpad.** Same technology stack (Kotlin, JavaFX, Gradle, kotlinx.serialization, AtlantaFX). Parallel folder structure. Separate Git repository. No shared code library.
- **Riemann sphere model.** All geometry lives on the unit sphere `{(x, y, z) : x² + y² + z² = 1}`. Geodesics are great circles (plane-sphere intersections). Circles are small circles (off-center plane-sphere intersections).
- **3D Cartesian coordinates.** Points store `(x, y, z)` rather than 2D coordinates. No singularities (unlike spherical coordinates). Spherical distance: `arccos(P · Q)`.
- **Orthographic projection.** "Looking at a globe" view. The visible hemisphere projects to the disk on screen. Geodesics project to elliptical arcs. The silhouette is the boundary circle.
- **Camera orientation via azimuth/elevation.** Two sliders plus Space+Drag control the view rotation. Azimuth: -180° to +180° (horizontal rotation around Y-axis). Elevation: -90° to +90° (vertical tilt). Default Front view: looking along -Z.
- **Poles on Y-axis.** North pole at (0, 1, 0), south pole at (0, -1, 0). X→screen-right, Y→screen-up in the default Front view. Grid meridians run pole-to-pole; parallels are circles of constant latitude.
- **No Ray tool.** A ray on the sphere is a degenerate concept (great circle minus one point). 8 toolbar buttons instead of 9. `GeodesicTool` has two sub-modes: `LINE` and `SEGMENT`.
- **No Transformation menu.** Mobius translations and rotations are specific to the hyperbolic plane. The camera rotation sliders provide the equivalent navigation for the sphere.
- **Back-face rendering at reduced opacity, toggleable.** Objects on the back hemisphere are visible at 0.3 opacity but non-interactive. Points, geodesics, and circles use proper split rendering at the silhouette. Regions use a simpler rule (deferred split rendering for v1). Back face is hidden by default; View > Show Back Face (Cmd+B) reveals it. The toggle is runtime-only (not serialized, not undoable). The export dialog has an independent "Include back face" checkbox (default: off).
- **Front-hemisphere-only interaction.** Point placement, dragging, hit-testing, and snapping all operate on the visible hemisphere only. Rotate the view to access the back.
- **Antipodal point handling.** Antipodal points are the sphere's equivalent of "coincident points" — they create degeneracies. Construction tools reject antipodal pairs. `recompute()` sets `isDefined = false` when parent points become antipodal via dragging.
- **Geodesic-geodesic intersection needs `sign` field.** Two distinct great circles always intersect at two antipodal points, unlike the Poincare disk where there's at most one intersection. All three intersection types use sign disambiguation.
- **Angle orientation is intrinsic and order-dependent.** Uses the outward normal at the vertex to define orientation. Clicking A, V, C measures the clockwise arc from A to C (as seen from outside the sphere), matching Euclid Sketchpad's convention. Swapping A and C produces the reflex angle. The renderers compare the 3D `measureRadians` against the screen-space sweep to decide small vs. large arc. View-independent — the angle measure doesn't change when rotating the camera.
- **Measurements are spherical.** Distance: `arccos(P · Q)`. Circumference: `2π sin(r)`. Area: spherical excess `(sum of angles) - (n-2)π`.
- **Elliptical arc rendering via `Path` with `ArcTo`.** JavaFX `ArcTo` supports elliptical arcs with `radiusX`, `radiusY`, and `xAxisRotation`, which is exactly what orthographic-projected great circles produce. SVG export uses the same elliptical arc parameters.
- **Screen Y-flip in ellipse rotation.** Model space is Y-up; JavaFX screen space is Y-down. The ellipse rotation angle must use `atan2(npx, -npy)` (negated Y) to correctly orient projected great circles and small circles. The edge-on fallback line direction is `(cosR, -sinR)` (the rotated major axis direction). Getting this wrong causes curves to miss their parent points by ~13% at typical non-default camera orientations.
- **Serialization uses `"model": "riemann"`.** No cross-compatibility with Poincare Sketchpad files. The `z` field is added to `SerializedObject` for 3D point coordinates.
- **Grid is meridians + parallels, on by default.** 12 meridians at 30° intervals + 5 parallels at 30° latitude intervals (including equator). Grid rotates with the camera view.
- **Screen-space angle arcs.** Same approach as Poincare Sketchpad — fixed pixel radius from vertex screen position. Tangent vectors projected to screen space for start/sweep angles.
- **Region split rendering deferred.** v1 uses a simple opacity rule (any vertex on front → full opacity, all on back → reduced). Proper split rendering at the silhouette for region edges is a future enhancement.
- **Overlay groups are mouseTransparent.** `objectGroup`, `previewGroup`, and `constructionPreviewGroup` are set to `isMouseTransparent = true` so that `sphereDisk` is always the JavaFX press-drag-release target. Without this, `render()` clears and rebuilds `objectGroup.children` during `onMousePressed`, detaching the original press target and breaking the drag gesture. All hit-testing is done manually via `HitTester`, so JavaFX node picking on these groups is never needed.
- **Hit-testing samples curves in 3D, not screen space.** `distanceToGeodesic()` and `distanceToCircle()` in `HitTester` use `circlePlaneBasis()` to get an orthonormal basis for the curve's plane, sample 3D points along the curve, check `isOnFrontHemisphere()`, then project to screen for distance. This avoids the ambiguity of orthographic projection where each screen point maps to two sphere points (front and back).
- **SelectionTool defers deselect to mouseReleased.** Clicking an already-selected object sets `pendingDeselect` instead of immediately removing it from `selectedIds`. If no drag occurs, `onMouseReleased` applies the deselect. This ensures drag can start on already-selected objects.

## Known Differences from Poincare Sketchpad

| Aspect | Poincare Sketchpad | Riemann Sketchpad |
|--------|-------------------|-------------------|
| Model | Poincare disk (2D) | Riemann sphere (3D) |
| Coordinates | `(x, y)` in open unit disk | `(x, y, z)` on unit sphere |
| Projection | Identity (model = screen) | Orthographic |
| Geodesics | Circular arcs (`Arc`) | Elliptical arcs (`ArcTo`) |
| Viewport control | Mobius transform + rotation (Space+Click/Drag) | Azimuth/elevation sliders + Space+Drag |
| Ray tool | Yes | No |
| Transformation menu | Mobius translation, rotation | None |
| Back-face | N/A (2D model) | Visible at 0.3 opacity, non-interactive |
| Geodesic-geodesic intersection | No sign field (at most 1 point in disk) | Sign field required (2 antipodal points) |
| Antipodal handling | N/A | Reject construction, undefined on recompute |
| Grid | Concentric hyperbolic circles + radial lines, off by default | Meridians + parallels, on by default |
| Measurement formulas | Hyperbolic | Spherical |
| Serialization model | `"poincare"` | `"riemann"` |
| App name | `"PoincareSketchpad"` | `"RiemannSketchpad"` |

## Known Issues

### ~~1. Export "Include grid" defaults to unchecked~~ (Fixed)

Fixed by setting `includeGrid = true` and `gridCb.isSelected = true` in `ExportDialog.kt`, matching the canvas default where the grid is on.

### ~~2. SVG export: missing back-face opacity and broken grid~~ (Fixed)

Fixed by adding silhouette-aware split rendering to `ExportRenderer.kt`. Lines, circles, and grid (meridians + parallels) now use `computeSilhouetteCrossings()` + `buildSvgSplitArcs()` with `BACK_FACE_OPACITY = 0.3` and `GRID_BACK_OPACITY = 0.15`, matching the canvas renderer. Edge-on checks also added for geodesics and grid meridians.

### ~~3. Angle rendering arc direction is inverted (canvas and SVG)~~ (Fixed)

Two bugs: (1) the `ArcTo` sweep flag was inverted (`!sweepFlag` on canvas, flipped condition on SVG), and (2) arc endpoint Y-coordinates were double-flipped — `atan2(-viewY, viewX)` already produces screen-space angles, but `svy - r*sin(θ)` negated Y again, placing endpoints on the wrong side of the vertex. Fixed by removing the `!` on the canvas sweep flag, flipping the SVG sweep flag condition, and changing `svy - r*sin(θ)` to `svy + r*sin(θ)` in both `GeometryRenderer.kt` and `ExportRenderer.kt`.

### ~~4. Angle hit-testing: cannot select angles with mouse~~ (Fixed)

Two bugs in `HitTester.isPointInAngleSector()`: (1) `mouseAngle` used Y-up convention (`atan2(-(my-svy), ...)`) while the tangent angles used Y-down convention (`atan2(-tAsy, ...)`), causing the clickable sector to be mirrored about the horizontal axis through the vertex; and (2) the sweep magnitude correction (`measureRadians > PI` check) present in `GeometryRenderer.renderAngle()` was missing, so reflex angles had an inverted clickable region. Fixed by changing `mouseAngle` to `atan2(my - svy, mx - svx)` and adding the `measureRadians`-based sweep correction.

### ~~5. Geodesic/circle hit-testing matches back-face portions~~ (Fixed)

`distanceToEllipseFrontHalf()` computed `modelZ = sqrt(1 - r²)` which is always positive inside the disk, so the `if (modelZ <= 0) continue` check never filtered anything. Root cause: screen-space points on a projected ellipse correspond to two 3D sphere points (front and back), so front-hemisphere filtering cannot be done from screen coordinates alone. Fixed by rewriting `distanceToGeodesic()` and `distanceToCircle()` to sample curves in 3D (using `circlePlaneBasis()` to get an orthonormal basis), call `isOnFrontHemisphere()` on the actual 3D point, then project to screen for distance measurement.

### 6. PDF export not implemented yet (Planned)

The agreed design is in `docs/SPEC.md` §12.7. It uses PDFBox and a shared SVG/PDF rendering walker, with SVG snapshot tests added first; PNG stays on `GeometryRenderer`. Pages are W×H points, and headless export writes 6 files. Targets v1.1.0. Labels use embedded DejaVu Sans. Still open: PDF metadata and the PDF test approach.
