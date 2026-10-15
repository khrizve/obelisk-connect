# Fonts

The Flutter app pulled Cinzel (headers) and Manrope (body) via
`google_fonts` at runtime. Qt has no equivalent, so this port bundles
the .ttf files instead — but they aren't included in this checkout.

Drop these two families' static .ttf files into this folder:

- **Cinzel** — https://fonts.google.com/specimen/Cinzel (OFL license)
- **Manrope** — https://fonts.google.com/specimen/Manrope (OFL license)

At minimum: `Cinzel-Regular.ttf`, `Cinzel-Bold.ttf` (or `-SemiBold`),
`Manrope-Regular.ttf`, `Manrope-SemiBold.ttf`. Filenames don't matter —
`CMakeLists.txt` globs everything in this folder and `main.cpp` loads
whatever it finds.

This step is optional: without any fonts here, the app runs fine and
just falls back to your system's default font instead of Cinzel/Manrope.
