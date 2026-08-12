/*
 * Port von ui/components/PixelButton.kt: der Pixel-Rahmen mit den
 * Treppenkanten (drawPixelBorder + drawLeft/RightSteppedBorder) und das
 * Lautsprecher-Motiv aus drawPixelIcon.
 *
 * Warum nicht einfach "border: 3px solid": Android zeichnet links und
 * rechts keinen geraden Strich, sondern eine Kante, die im obersten und
 * untersten Viertel doppelt so breit ist — genau das macht den Retro-Look
 * aus. Ein CSS-Rahmen kann das nicht. Wir bauen dieselbe Geometrie
 * stattdessen aus Hintergrund-Layern (linear-gradient): die bleiben bei
 * jeder Pixeldichte scharf (keine Skalierung, kein Bild, keine
 * Unschaerfe) und wachsen mit dem Knopf mit.
 */
(function (global) {
  "use strict";

  /**
   * Rechtecke des Rahmens (Port von drawPixelBorder). Koordinaten relativ
   * zur linken oberen Ecke des Knopfs, alles in derselben Einheit wie w/h.
   *
   * `overlap` entspricht dem "+1f" aus Kotlin: dort wird jede Treppenstufe
   * einen Geraete-Pixel hoeher gezeichnet, damit zwischen den Stufen keine
   * Luecke aufblitzt. Aufrufer geben hier 1/devicePixelRatio, damit es im
   * Browser ebenfalls genau ein Geraete-Pixel ist.
   */
  function steppedBorderRects(w, h, b, overlap) {
    if (overlap === undefined) overlap = 1;
    var rects = [
      { x: 0, y: 0, w: w, h: b },       // oben durchgehend
      { x: 0, y: h - b, w: w, h: b }    // unten durchgehend
    ];

    // Kotlin: steps = ((height - 2 * pixelSize) / pixelSize).toInt()
    var steps = Math.trunc((h - 2 * b) / b);
    if (steps > 0) {
      var stepH = (h - 2 * b) / steps;
      // Kotlin rechnet mit Int-Division: i < steps/4 bzw. i >= steps*3/4
      // sind die breiten Abschnitte. Aufeinanderfolgende Stufen gleicher
      // Breite fassen wir zu einem Rechteck zusammen — die Vereinigung
      // ist identisch, weil jede Stufe um `overlap` in die naechste ragt.
      var q1 = Math.trunc(steps / 4);
      var q3 = Math.trunc((steps * 3) / 4);
      var runs = [
        [0, q1, b * 2],
        [q1, q3, b],
        [q3, steps, b * 2]
      ];
      for (var i = 0; i < runs.length; i++) {
        var from = runs[i][0], to = runs[i][1], sw = runs[i][2];
        if (to <= from) continue;
        var y = b + from * stepH;
        var rh = (to - from) * stepH + overlap;
        rects.push({ x: 0, y: y, w: sw, h: rh });        // linke Kante
        rects.push({ x: w - sw, y: y, w: sw, h: rh });   // rechte Kante
      }
    }
    return rects;
  }

  /**
   * Baut aus den Rahmen-Rechtecken die drei CSS-Werte fuer
   * background-image / -size / -position. Volle Breite wird als "100%"
   * gesetzt, damit ein Knopf auch dann sauber bleibt, wenn ihn ein langer
   * Text breiter macht als beim Berechnen.
   */
  function borderCss(w, h, b, overlap, color) {
    var rects = steppedBorderRects(w, h, b, overlap);
    var images = [], sizes = [], positions = [];
    for (var i = 0; i < rects.length; i++) {
      var r = rects[i];
      images.push("linear-gradient(" + color + "," + color + ")");
      sizes.push((r.w >= w ? "100%" : r.w + "px") + " " + r.h + "px");
      // Rechte Kante buendig an den rechten Rand: "100%" richtet die
      // rechte Bildkante an der rechten Knopfkante aus. Ein
      // "calc(100% - Breite)" waere falsch — background-position mischt
      // Prozent und Laenge als (Knopf - Bild) * 100% + Laenge und zoege
      // die Kante um ihre eigene Breite nach innen.
      positions.push((r.x === 0 ? "0px" : "100%") + " " + r.y + "px");
    }
    return {
      image: images.join(","),
      size: sizes.join(","),
      position: positions.join(",")
    };
  }

  /** Setzt Hintergrund und Treppen-Rahmen auf einen Knopf. */
  function apply(el, backgroundColor, borderColor, borderWidth) {
    var style = global.getComputedStyle ? global.getComputedStyle(el) : null;
    // Ueber die berechnete Breite/Hoehe statt offsetWidth: die Knoepfe im
    // Game-Over liegen beim Start in einem ausgeblendeten Overlay und
    // haetten dort offsetWidth 0.
    var w = style ? parseFloat(style.width) : el.offsetWidth;
    var h = style ? parseFloat(style.height) : el.offsetHeight;
    var dpr = global.devicePixelRatio || 1;
    var css = borderCss(w, h, borderWidth, 1 / dpr, borderColor);
    el.style.backgroundColor = backgroundColor;
    el.style.backgroundImage = css.image;
    el.style.backgroundSize = css.size;
    el.style.backgroundPosition = css.position;
    el.style.backgroundRepeat = "no-repeat";
  }

  /**
   * Lautsprecher-Motiv aus drawPixelIcon: Bloecke auf dem 16er-Raster,
   * "aus" bekommt zusaetzlich die rote Treppen-Durchstreichung. Bewusst
   * ohne shape-rendering="crispEdges": Compose zeichnet die Bloecke mit
   * Kantenglaettung, und die Koordinaten liegen auf halben Rastereinheiten.
   *
   * Das Glocken-Motiv (BELL_ON/BELL_OFF) fehlt hier, weil die PWA keine
   * Tages-Erinnerung hat — siehe README.
   */
  function speakerSvg(muted, color, strikeColor) {
    var rects =
      '<rect x="3" y="6" width="2.5" height="4"/>' +
      '<rect x="5.5" y="5" width="1.5" height="6"/>' +
      '<rect x="7" y="4" width="1.5" height="8"/>';
    if (!muted) {
      rects +=
        '<rect x="10" y="6" width="1.2" height="4"/>' +
        '<rect x="12" y="4.5" width="1.2" height="7"/>';
    }
    var strike = "";
    if (muted) {
      for (var i = 0; i < 6; i++) {
        strike += '<rect x="' + (2.5 + i * 1.9) + '" y="' + (2.5 + i * 1.9) +
          '" width="2.2" height="2.2" fill="' + strikeColor + '"/>';
      }
    }
    return '<svg viewBox="0 0 16 16" width="100%" height="100%" aria-hidden="true">' +
      '<g fill="' + color + '">' + rects + "</g>" + strike + "</svg>";
  }

  var PixelButton = {
    steppedBorderRects: steppedBorderRects,
    borderCss: borderCss,
    apply: apply,
    speakerSvg: speakerSvg
  };

  global.PixelButton = PixelButton;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = PixelButton;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);
