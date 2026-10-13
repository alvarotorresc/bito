"""Utilidades compartidas por las herramientas de arte de Habi.

Solo numpy y Pillow. Todas las coordenadas van en píxeles de la imagen de entrada salvo donde se
dice «coordenadas del cuerpo» (cuerpo.png mide 1132 x 1404 y está recortado en ORIGEN_CUERPO del
lienzo de 2048 de la base).
"""
import numpy as np

# Recorte del magenta: m >= T0 es fondo puro, m <= T1 es opaco (valores del prototipo).
MAGENTA_T0 = 150.0
MAGENTA_T1 = 70.0
# Por encima de este magenta un píxel es candidato a fondo. Un traje lila llega a 56.
MAGENTA_FONDO = 60.0
# Un hueco interior solo es fondo si tiene al menos este número de píxeles de magenta casi puro.
HUECO_MIN = 150
# Spec §8.2: escala y desplazamiento del contorno respecto a la base, como mucho un 2 %.
UMBRAL_ALINEACION = 0.02
# Spec §8.2: recorte limpio. Fracción máxima de píxeles de borde con tinte magenta.
UMBRAL_HALO = 0.02
# Origen de cuerpo.png dentro del lienzo de 2048 de la base.
ORIGEN_CUERPO = (478, 366)
# Zona de ojos y boca en coordenadas del cuerpo (x0, y0, x1, y1): el hueco de un disfraz la deja ver.
CAJA_CARA = (400, 480, 860, 720)
# Fracción mínima de piel verde dentro de CAJA_CARA para dar por bueno el hueco de un disfraz.
MINIMO_HUECO = 0.6
# Un disfraz mide el centro del cuerpo en esta fracción de la altura de Habi, justo sobre los pies.
# Medido con el dinosaurio aprobado: su cola baja hasta el 8 % y con una franja del 10 % o más el
# centro se corre 0,17 anchos; con el 5 % solo se ven los pies.
FRANJA_PIES = 0.05
# Búsqueda de alineación: escalas y desplazamiento máximo (px a resolución completa).
ESCALAS = tuple(np.arange(0.96, 1.0401, 0.01))
DESPLAZAMIENTO_MAX = 56


def cargar_rgb(ruta):
    from PIL import Image

    return np.asarray(Image.open(ruta).convert("RGB")).astype(np.float32)


def magentez(rgb):
    """Cuánto magenta tiene cada píxel: min(R, B) - G (0..255)."""
    return np.minimum(rgb[..., 0], rgb[..., 2]) - rgb[..., 1]


def _etiquetar(m):
    """Etiquetas de componentes conexas (8 vecinos) y el número de píxeles de cada una.

    Trabaja por tramos horizontales con unión-búsqueda: rápido aunque la imagen mida 2048 px.
    """
    m = np.asarray(m, bool)
    etiquetas = np.zeros(m.shape, np.int32)
    alto, ancho = m.shape
    if not m.any():
        return etiquetas, [0]
    d = np.diff(np.pad(m.astype(np.int8), ((0, 0), (1, 1))), axis=1)
    ys, xs = np.nonzero(d == 1)
    _, xe = np.nonzero(d == -1)
    ys, xs, xe = ys.tolist(), xs.tolist(), xe.tolist()
    n = len(ys)
    padre = list(range(n))

    def raiz(i):
        while padre[i] != i:
            padre[i] = padre[padre[i]]
            i = padre[i]
        return i

    comienzo = [0] * (alto + 1)
    for y in ys:
        comienzo[y + 1] += 1
    for y in range(alto):
        comienzo[y + 1] += comienzo[y]
    for y in range(1, alto):
        i, fin_i = comienzo[y - 1], comienzo[y]
        j, fin_j = comienzo[y], comienzo[y + 1]
        while i < fin_i and j < fin_j:
            if xs[i] <= xe[j] and xs[j] <= xe[i]:
                a, b = raiz(i), raiz(j)
                if a != b:
                    padre[b] = a
            if xe[i] <= xe[j]:
                i += 1
            else:
                j += 1
    nuevo = {}
    tamanos = [0]
    for k in range(n):
        r = raiz(k)
        if r not in nuevo:
            nuevo[r] = len(tamanos)
            tamanos.append(0)
        etiqueta = nuevo[r]
        etiquetas[ys[k], xs[k] : xe[k]] = etiqueta
        tamanos[etiqueta] += xe[k] - xs[k]
    return etiquetas, tamanos


def rellenar_huecos(mascara):
    """La máscara con sus huecos interiores rellenos: solo queda fuera lo que llega al borde."""
    m = np.asarray(mascara, bool)
    etiquetas, _ = _etiquetar(~m)
    borde = np.concatenate([etiquetas[0], etiquetas[-1], etiquetas[:, 0], etiquetas[:, -1]])
    fuera = set(np.unique(borde).tolist()) - {0}
    return m | ((etiquetas > 0) & ~np.isin(etiquetas, list(fuera)))


def quitar_fondo_magenta(rgb):
    """Máscara del fondo: magenta conectado al borde más los huecos interiores grandes."""
    m = magentez(rgb)
    etiquetas, _ = _etiquetar(m > MAGENTA_FONDO)
    borde = np.concatenate([etiquetas[0], etiquetas[-1], etiquetas[:, 0], etiquetas[:, -1]])
    fondo_ids = set(np.unique(borde).tolist()) - {0}
    puro = np.bincount(etiquetas[m > MAGENTA_T0], minlength=etiquetas.max() + 1)
    fondo_ids |= {i for i in np.nonzero(puro > HUECO_MIN)[0].tolist() if i}
    return np.isin(etiquetas, list(fondo_ids)) if fondo_ids else np.zeros(m.shape, bool)


def quitar_magenta(rgb, t0=MAGENTA_T0, t1=MAGENTA_T1):
    """RGB float -> RGBA uint8 con el fondo transparente y el borde sin tinte magenta.

    Solo es fondo el magenta conectado al borde de la imagen y los huecos interiores de magenta casi
    puro: un traje lila (hasta 56) o rosa dentro de la pieza se queda opaco y con su color. El
    despill actúa solo sobre el fondo y su borde.
    """
    m = magentez(rgb)
    fondo = quitar_fondo_magenta(rgb)
    alfa = np.where(fondo, np.clip((t0 - m) / (t0 - t1), 0, 1), 1.0)
    k = np.where(fondo & (m > 0), m, 0)
    color = np.stack(
        [np.clip(rgb[..., 0] - k, 0, 255), rgb[..., 1], np.clip(rgb[..., 2] - k, 0, 255)],
        -1,
    )
    return np.dstack([color, alfa * 255]).astype(np.uint8)


def componentes(mascara, paso=1, area_min=1):
    """Cajas (y0, x0, y1, x1, area) de las componentes conexas de una máscara booleana.

    Con paso > 1 trabaja sobre la máscara reducida y devuelve cajas en coordenadas completas.
    """
    m = mascara[::paso, ::paso]
    etiquetas, tamanos = _etiquetar(m)
    cajas = []
    for etiqueta in range(1, len(tamanos)):
        area = tamanos[etiqueta] * paso * paso
        if area < area_min:
            continue
        ys, xs = np.nonzero(etiquetas == etiqueta)
        cajas.append((ys.min() * paso, xs.min() * paso, (ys.max() + 1) * paso, (xs.max() + 1) * paso, area))
    return cajas


def mayor_componente(mascara, paso=2):
    """La componente conexa más grande de la máscara, a resolución completa."""
    return componentes_grandes(mascara, paso=paso, fraccion=1.0)


def componentes_grandes(mascara, paso=2, fraccion=0.15, cambio=None, cambio_min=18):
    """Las componentes con al menos `fraccion` de la mayor (un zapato por pie, no solo uno).

    Si se pasa `cambio` (diferencia por píxel respecto a la base), se descartan las componentes que
    no cambiaron (diferencia media <= cambio_min) salvo la mayor.
    """
    m = mascara[::paso, ::paso]
    etiquetas, tamanos = _etiquetar(m)
    if len(tamanos) == 1:
        return np.zeros_like(mascara, dtype=bool)
    mayor = int(np.argmax(tamanos[1:])) + 1
    ids = [k for k in range(1, len(tamanos)) if tamanos[k] >= fraccion * tamanos[mayor]]
    if cambio is not None:
        pequeno = cambio[::paso, ::paso]
        ids = [k for k in ids if k == mayor or pequeno[etiquetas == k].mean() > cambio_min]
    grande = np.kron(np.isin(etiquetas, ids), np.ones((paso, paso), bool))
    grande = grande[: mascara.shape[0], : mascara.shape[1]]
    return grande & mascara


def hsv(rgb):
    """Tono en grados, saturación y valor (0..1) de una imagen RGB float 0..255."""
    a = rgb / 255.0
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    mx, mn = a.max(-1), a.min(-1)
    d = mx - mn + 1e-6
    h = np.where(mx == r, ((g - b) / d) % 6, np.where(mx == g, (b - r) / d + 2, (r - g) / d + 4)) * 60
    s = d / (mx + 1e-6)
    return h, s, mx


def mascara_verde(rgb):
    """La piel de Habi (verde salvia), excluyendo el fondo magenta.

    Los negros con tinte verde no son Habi: hace falta v > 0,30. El cuerpo real tiene s entre 0,15 y
    0,22, así que el umbral de saturación baja hasta 0,08.
    """
    h, s, v = hsv(rgb)
    return (h > 70) & (h < 130) & (s > 0.08) & (s < 0.34) & (v > 0.30) & (magentez(rgb) / 255.0 <= 0.35)


def contorno(mascara):
    """(x0, x1, y1) de la máscara, ignorando el 0,2 % extremo de ruido."""
    ys, xs = np.nonzero(mascara)
    x0, x1 = np.percentile(xs, [0.2, 99.8])
    y1 = np.percentile(ys, 99.8)
    return float(x0), float(x1), float(y1)


def pies(mascara, altura_habi):
    """(centro x, y de los pies) de una máscara, midiendo el centro solo en la franja de los pies.

    La franja es FRANJA_PIES de la altura de Habi justo sobre la línea de los pies: a esa altura no
    llegan ni las astas ni una cola que cuelga, y el centro no se corre.
    """
    ys, xs = np.nonzero(mascara)
    y1 = float(np.percentile(ys, 99.8))
    en_franja = ys >= y1 - FRANJA_PIES * altura_habi
    x0, x1 = np.percentile(xs[en_franja], [0.2, 99.8])
    return float((x0 + x1) / 2), y1


def desviacion(base, editada):
    """Lo que la editada se ha escalado o movido respecto a la base, en fracción del ancho de Habi.

    Ambas son contornos (x0, x1, y1).
    """
    bx0, bx1, by1 = base
    ex0, ex1, ey1 = editada
    ancho = bx1 - bx0
    return max(abs((ex1 - ex0) / ancho - 1), abs(ex0 - bx0) / ancho, abs(ey1 - by1) / ancho)


def desviacion_alineacion(escala, dx, dy, ancho):
    """Desviación de una alineación encontrada: |escala - 1| y el desplazamiento en anchos de Habi."""
    return max(abs(escala - 1), abs(dx) / ancho, abs(dy) / ancho)


def desviacion_disfraz(base, editada, ancho):
    """Un disfraz es más ancho que Habi: solo cuentan el centro y la línea de los pies.

    `base` y `editada` son (centro x, y de los pies), de `pies`. `ancho` es el de Habi.
    """
    return max(abs(editada[0] - base[0]) / ancho, abs(editada[1] - base[1]) / ancho)


def _reducir(mascara, f):
    from PIL import Image

    im = Image.fromarray((mascara * 255).astype(np.uint8))
    return np.asarray(im.resize((mascara.shape[1] // f, mascara.shape[0] // f), Image.BILINEAR)) > 127


def alinear(piel_base, piel_editada):
    """Escala y desplazamiento de la editada que maximizan el solape (IoU) de la piel con la base.

    El encuadre por contorno falla si el accesorio tapa un lado (auriculares, bufanda); el solape de
    la piel que queda a la vista, no. Devuelve (iou, escala, dx, dy) con dx, dy en píxeles
    completos: la editada se escala por `escala` y se pega en (dx, dy).
    """
    from PIL import Image

    f = 4 if max(piel_base.shape) >= 1024 else 2
    b = _reducir(piel_base, f)
    e = _reducir(piel_editada, f)
    rango = DESPLAZAMIENTO_MAX // f
    area_b = np.count_nonzero(b)
    mejor = (-1.0, 1.0, 0, 0)
    for sc in ESCALAS:
        h, w = e.shape
        im = Image.fromarray((e * 255).astype(np.uint8))
        es = np.asarray(im.resize((int(w * sc), int(h * sc)), Image.NEAREST)) > 127
        area_es = np.count_nonzero(es)
        for dy in range(-rango, rango + 1):
            for dx in range(-rango, rango + 1):
                ys0, xs0 = max(dy, 0), max(dx, 0)
                ye, xe = min(es.shape[0] + dy, b.shape[0]), min(es.shape[1] + dx, b.shape[1])
                if ye <= ys0 or xe <= xs0:
                    continue
                trozo = es[ys0 - dy : ye - dy, xs0 - dx : xe - dx]
                base = b[ys0:ye, xs0:xe]
                inter = np.count_nonzero(trozo & base)
                union = area_es + area_b - inter
                iou = inter / union if union else 0.0
                if iou > mejor[0]:
                    mejor = (iou, float(sc), dx * f, dy * f)
    return mejor


def fraccion_verde(rgb, caja):
    """Fracción de piel verde dentro de caja = (x0, y0, x1, y1)."""
    x0, y0, x1, y1 = caja
    return float(mascara_verde(rgb[y0:y1, x0:x1]).mean())


def halo(rgba):
    """Fracción de píxeles de borde (alfa entre 10 % y 98 %) que conservan tinte magenta."""
    alfa = rgba[..., 3].astype(np.float32)
    borde = (alfa > 25) & (alfa < 250)
    if not borde.any():
        return 0.0
    m = magentez(rgba[..., :3].astype(np.float32))
    return float((m[borde] > 40).mean())
