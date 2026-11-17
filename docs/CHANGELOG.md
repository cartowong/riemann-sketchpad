# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.0.0] - Unreleased

### Added

- AngleTool preview: green arc sector shown during 3rd-point selection, with intersection ghost dots
- RegionTool preview: geodesic arc edges between placed vertices, preview arc to cursor, yellow arc-filled polygon and highlighted first vertex on close hover, with intersection ghost dots
- Construction application preview now renders Angle (arc sector) and Region (filled polygon) objects, matching EuclidSketchpad's 4-pass preview rendering
- View > Show Back Face (Cmd+B) toggle to show/hide back-hemisphere geometry; hidden by default, runtime-only, not serialized or undoable
- Export dialog "Include back face" checkbox (default: off) controlling whether back-face portions appear in PNG/SVG output

### Changed

- Window title changed from "RiemannSketchpad 1.0.0" to "Riemann Sketchpad" and app icon added to the title bar
- About dialog redesigned to match Poincaré Sketchpad: app icon, GitHub link, third-party licenses link, updated copyright and license info

### Fixed

- GeodesicTool and CircleTool missing intersection preview ghost dots and snap-to-intersection in onMouseMoved; all tools now show ghost dots and snap preview endpoints to intersection candidates and constructed points, matching EuclidSketchpad
- RegionTool preview arc used raw cursor position instead of intersection 3D coordinates for geodesic arc computation
- Geodesic and circle hit-testing responding to clicks on back-face (faded) portions; `distanceToEllipseFrontHalf()` front-hemisphere check was a no-op (`sqrt(1-r²)` always positive inside disk). Rewrote to sample curves in 3D and call `isOnFrontHemisphere()` on actual sphere points
- Angle hit-testing used opposite Y-convention for mouse vs tangent angles and was missing sweep magnitude correction, making angles unselectable with the mouse cursor
- Angle orientation now matches Euclid Sketchpad: clicking A, V, C draws the small arc; reversing A and C draws the reflex arc. Renderers compare 3D `measureRadians` against screen-space sweep to pick the correct arc direction (canvas and SVG)
- Export dialog checkbox labels ("Include back face") truncated in 450px-wide dialog; split options into two rows
- SVG export only applying back-face opacity (0.3) to points; now also applies to geodesic segments, circles, angles, and regions
- Angle arc rendering inverted and misaligned with adjacent segments (canvas and SVG); sweep flag was negated and arc endpoint Y-coordinates were double-flipped

- Clicking a row in the Objects table throwing IndexOutOfBoundsException; selection listener's synchronous render re-entered the table during clearAndSelect, now deferred via Platform.runLater
- Sidebar tables (object list, constructions, measurements) showing an extra empty column; now use CONSTRAINED_RESIZE_POLICY to fill available width
- Sidebar tables not updating when objects are constructed on the canvas; added onRender callback so every render refreshes the tables
- Left/right pane divider positions (0.20/0.80) not matching sister projects; changed to 0.30/0.70
- Dragging points not working because rendered shapes in objectGroup intercepted JavaFX press-drag-release gesture; objectGroup, previewGroup, and constructionPreviewGroup are now mouseTransparent
- SelectionTool deselecting on press instead of release, causing click-drag to fail every other attempt; deselect-toggle now deferred to mouseReleased when no drag occurred
- PointTool not supporting immediate drag after point creation; newly created points can now be repositioned without switching to SelectionTool
- Back-face opacity invisible for great circles, small circles, and grid lines (front ellipse completely covered identical back ellipse); now properly split at the silhouette boundary into front and back arcs
- SVG export rendering lines, circles, and grid without silhouette-aware split arcs or back-face opacity; now uses `computeSilhouetteCrossings` + `buildSvgSplitArcs` matching the canvas renderer (`BACK_FACE_OPACITY = 0.3`, `GRID_BACK_OPACITY = 0.15`)
- Export dialog "Include grid" checkbox defaulting to unchecked despite canvas grid being on by default
- File > Open showing false "unsaved changes" dialog on fresh launch because `markSaved()` was not called after the initial undo checkpoint
- Line tool preview now shows front/back opacity split matching the final rendered line
- Geodesic and circle rendering not passing through parent points at non-default camera orientations (ellipse rotation angle missing screen Y-flip)
- Edge-on geodesic/grid lines drawn along the normal direction instead of perpendicular to it

### Added

- Riemann sphere geometry engine (great circles, circles, intersections, angles, regions)
- 8 construction tools: Select, Point, Segment, Line, Circle, Angle, Region, Label
- Snapshot-based undo/redo system
- Custom construction recording, saving, loading, and applying with preview
- 3 built-in constructions: Midpoint, Perpendicular Line, Reflection Point
- Orthographic projection with azimuth/elevation camera controls
- Object list, measurements table, and custom constructions table sidebars
- PNG and SVG export (Canvas and Custom methods) with headless CLI mode
- Spherical grid (meridians + parallels), on by default
- JSON serialization for diagrams and constructions with model validation
