# Design Tokens (Canonical - Visual Parity)

> Campzone has one brand. Web and Android must reuse these exact tokens
> so the three apps look like one product. Values are extracted from the
> iOS asset catalog (`Resources/Assets.xcassets/Colors/*.colorset`) and
> `Core/DesignSystem` (`CZLayout.swift`, `CZFonts.swift`). **No
> hardcoded raw colors/sizes in feature code - reference tokens only.**

The brand is a warm “campfire” palette: ember/flame orange primary,
pine/forest greens, cream/stone neutrals, with full light + dark
variants. SwiftUI uses `design: .rounded` everywhere - use a rounded
typeface on web/Android for parity.

---

## 1. Semantic colors (hex, light “any” / dark)

| Token | Light | Dark | Use |
| --- | --- | --- | --- |
| `czBackground` | `#F8F4EE` | `#070E1A` | screen background |
| `czSurface` | `rgba(0,0,0,0.039)` | `rgba(255,255,255,0.078)` | cards/elevated surfaces (overlay tint) |
| `czPrimary` | `#FF6B35` | `#FF7A47` | primary brand / CTAs (ember) |
| `czSecondary` | `#3A6248` | `#4A7C59` | secondary (pine green) |
| `czAccent` | `#D97706` | `#FFB347` | accents/links/highlights |
| `czTextPrimary` | `#1C1917` | `#FFF4E0` | primary text |
| `czTextSecondary` | `#6B6052` | `#C4A875` | secondary text |
| `czSuccess` | `#16A34A` | `#66BB6A` | success state/toasts |
| `czWarning` | `#D97706` | `#FFB347` | warning state |
| `czError` | `#DC2626` | `#FF6B6B` | error/destructive |
| `czDivider` | `rgba(0,0,0,0.078)` | `rgba(255,255,255,0.122)` | hairline dividers |

Brand/decorative extras (same idea - light/dark colorsets in the iOS
asset catalog; pull the exact pair from there if you use one):
`czEmber` `#FF6B35`/`#FF7A47`, `czFlame` `#FF8C00`/`#FFA133`, `czAmber`
`#FFB347`/`#FFC266`, `czPine` `#243824`/`#2F4A2F`, plus
`czForest`, `czHorizon`, `czStone`, `czTwilight`, `czLeaf`, `czCream`
(decorative gradients/illustrations - match if you reproduce those
surfaces; otherwise stick to the semantic set above).

Surface/divider tokens are **alpha overlays** over the background (not
opaque) - implement them as `rgba(...)` layered on `czBackground`, not
as a flat color, so they read correctly in both schemes.

## 2. Typography (SF Pro **Rounded**)

| Token | iOS size/weight | Web/Android target |
| --- | --- | --- |
| `czLargeTitle` | 34, bold | display, 34/700 |
| `czTitle` | 28, bold | h1, 28/700 |
| `czTitle2` | 22, semibold | h2, 22/600 |
| `czTitle3` | 20, semibold | h3, 20/600 |
| `czHeadline` | 17, semibold | headline, 17/600 |
| `czBody` | 17, regular | body, 17/400 |
| `czCallout` | 16, regular | callout, 16/400 |
| `czSubhead` | 15, medium | subhead, 15/500 |
| `czCaption` | 12, regular | caption, 12/400 |
| `czCaption2` | 11, medium | caption2, 11/500 |

Use a **rounded** family for brand parity (e.g. `"SF Pro Rounded"` →
fallback `system-ui` rounded; Android: a rounded `FontFamily`, or
`Nunito`/`Varela Round`). Sizes are **Dynamic Type** on iOS - web must
respect `rem`/user zoom; Android must use `sp` and respect font scale.

## 3. Spacing scale (`CZSpacing`, points → px/dp)

`xs 4` · `sm 8` · `md 12` · `base 16` · `lg 20` · `xl 24` · `xxl 32` ·
`xxxl 48`. Use only these step values for padding/gaps.

## 4. Corner radius (`CZRadius`)

`xs 4` · `sm 8` · `md 12` · `lg 16` · `xl 20` · `xxl 24` · `full 999`
(capsule/pill). Cards/compact UI use sm–lg; pills use `full`.

## 5. Web implementation (Tailwind v4 / CSS variables)

`globals.css` (replace the scaffold defaults):

```css
@import "tailwindcss";

:root {
  --cz-background: #F8F4EE;
  --cz-surface: rgba(0,0,0,0.039);
  --cz-primary: #FF6B35;
  --cz-secondary: #3A6248;
  --cz-accent: #D97706;
  --cz-text-primary: #1C1917;
  --cz-text-secondary: #6B6052;
  --cz-success: #16A34A;
  --cz-warning: #D97706;
  --cz-error: #DC2626;
  --cz-divider: rgba(0,0,0,0.078);
  --cz-ember:#FF6B35; --cz-flame:#FF8C00; --cz-amber:#FFB347; --cz-pine:#243824;
  --cz-radius-sm:8px; --cz-radius-md:12px; --cz-radius-lg:16px; --cz-radius-full:999px;
  --cz-space-xs:4px; --cz-space-sm:8px; --cz-space-md:12px; --cz-space-base:16px;
  --cz-space-lg:20px; --cz-space-xl:24px; --cz-space-xxl:32px; --cz-space-xxxl:48px;
}
@media (prefers-color-scheme: dark) {
  :root {
    --cz-background:#070E1A; --cz-surface:rgba(255,255,255,0.078);
    --cz-primary:#FF7A47; --cz-secondary:#4A7C59; --cz-accent:#FFB347;
    --cz-text-primary:#FFF4E0; --cz-text-secondary:#C4A875;
    --cz-success:#66BB6A; --cz-warning:#FFB347; --cz-error:#FF6B6B;
    --cz-divider:rgba(255,255,255,0.122);
    --cz-ember:#FF7A47; --cz-flame:#FFA133; --cz-amber:#FFC266; --cz-pine:#2F4A2F;
  }
}
@theme inline {
  --color-cz-background: var(--cz-background);
  --color-cz-primary: var(--cz-primary);
  --color-cz-text: var(--cz-text-primary);
  /* …expose the rest as Tailwind colors as needed… */
}
```

Centralize tokens (no inline hex in components). Mirror the iOS
`CZButton`/`CZCard`/`CZTextField`/`CZBadge`/`CZEmptyState`/
`CZErrorState`/`CZLoadingView`/`CZAvatar`/`CZSectionHeader` set as
React components in a shared `components/cz/` (or `ui/`) folder.

## 6. Android implementation (Compose)

Define a `CzColors` object (light/dark) + a `CompositionLocal` /
Material3 `ColorScheme`, a `CzSpacing`/`CzRadius` `object` with `Dp`
values, and a rounded `Typography`. Example:

```kotlin
val CzPrimaryLight = Color(0xFFFF6B35)
val CzPrimaryDark  = Color(0xFFFF7A47)
val CzBackgroundLight = Color(0xFFF8F4EE)
val CzBackgroundDark  = Color(0xFF070E1A)
// …all semantic tokens (light/dark)…

object CzSpacing { val xs=4.dp; val sm=8.dp; val md=12.dp; val base=16.dp
  val lg=20.dp; val xl=24.dp; val xxl=32.dp; val xxxl=48.dp }
object CzRadius  { val xs=4.dp; val sm=8.dp; val md=12.dp; val lg=16.dp
  val xl=20.dp; val xxl=24.dp; val full=999.dp }
```

Wire into a `CampzoneTheme { ... }` that swaps light/dark by
`isSystemInDarkTheme()`. Build a reusable component set mirroring the
iOS design system. Respect Dynamic Type (font scale) and provide
content descriptions (accessibility parity - see `00-project-overview`).
