Inter (static TTF, Regular/SemiBold/Bold/Italic)

Source:  https://github.com/rsms/inter (the official, canonical Inter typeface project)
Release: v4.1 (https://github.com/rsms/inter/releases/tag/v4.1)
Files:   extras/ttf/Inter-Regular.ttf, extras/ttf/Inter-SemiBold.ttf, extras/ttf/Inter-Bold.ttf,
         extras/ttf/Inter-Italic.ttf - the static (non-variable) per-weight instances from that
         release, chosen because they are ordinary, single-weight TTF outlines PDFBox's
         PDType0Font/TrueTypeFont loader renders directly; the release's variable-font files
         (InterVariable.ttf, Inter.ttc) require font-axis instancing PDFBox does not perform, so
         the static build is the correct artifact for this renderer, not merely the smallest one.

License: SIL Open Font License, Version 1.1 - see OFL.txt in this directory (copied unmodified
         from the release's LICENSE.txt). Same license family already accepted in this project for
         Open Sans (com.helger.font:ph-fonts-open-sans) - free to use, embed, and redistribute
         bundled with this application; the font itself may not be sold on its own.

Vendored directly into this project's build (not fetched at runtime, no host-font dependency) per
Sprint 11 Golden Master Typography Calibration - no Maven-packaged Inter distribution with static
per-weight TTF files was available (the one webjars.npm:inter-ui artifact found on Maven Central
ships only browser WOFF2, which PDFBox cannot load).
