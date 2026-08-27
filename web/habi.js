/* ==========================================================================
   Bito · Habi vivo — la mirada
   Compartido por todos los idiomas de la landing: aqui no hay ni una cadena de
   texto ni una ruta, solo geometria.

   Reparto de tareas: la respiracion y el parpadeo son CSS puro (estilo.css) y
   este fichero no los toca. Lo unico que hace es escribir dos propiedades
   personalizadas en el grupo de los ojos, --habi-mirada-x y --habi-mirada-y,
   ambas entre -1 y 1. El radio en unidades del SVG (0,014 x 0,010, los
   GAZE_SHIFT del avatar de la app) lo pone la hoja de estilo.

   Que escriba variables y nunca `style.transform` no es un detalle: la regla
   que las usa vive dentro de `@media (prefers-reduced-motion: no-preference)`,
   asi que con la reduccion de movimiento activada no existe transformacion
   alguna que anular. Un `style.transform` en linea se habria saltado la media
   query. Aun asi, con reduccion activa este fichero ni siquiera arranca: no
   escucha el puntero y no pide un solo fotograma.
   ========================================================================== */

(function () {
  'use strict';

  var vivos = [];
  var nodos = document.querySelectorAll('.habi-vivo .habi-mirada');
  for (var i = 0; i < nodos.length; i++) {
    var el = nodos[i];
    vivos.push({
      el: el,
      caja: el.closest ? (el.closest('.habi-vivo') || el) : el,
      medida: null,
      x: null,
      y: null
    });
  }
  if (!vivos.length) return;

  // A que distancia tiene que estar el puntero para que la mirada llegue al
  // tope, en multiplos del lado de Habi. 1,4 hace que mire a lo que pasa cerca
  // de el y sature con lo que le queda lejos, como haria un bicho.
  var ALCANCE = 1.4;

  // Deriva de reposo: la que se ve en tactil, donde no hay cursor al que
  // seguir, y mientras nadie ha movido todavia el raton. Dos senos por eje con
  // periodos que no son multiplos entre si, para que el recorrido no se repita
  // a la vista. Las amplitudes suman menos de 1 en cada eje.
  var DERIVA = [
    { p1: 13100, a1: 0.62, p2: 7900, a2: 0.34, fase: 0.0 },
    { p1: 11300, a1: 0.55, p2: 5700, a2: 0.28, fase: 1.1 }
  ];

  // Un fotograma de deriva cada 90 ms basta: la transicion de 280 ms de la hoja
  // de estilo interpola lo que falta, y el movil no paga 60 fps por una mirada.
  var PASO_DERIVA = 90;

  var menos = window.matchMedia('(prefers-reduced-motion: reduce)');
  var puntero = window.PointerEvent ? 'pointermove' : 'mousemove';
  var pasivo = { passive: true };

  var encendido = false;
  var cuadro = 0;
  var ultimoCuadro = -Infinity;
  var ultimoX = 0;
  var ultimoY = 0;
  var siguiendo = false; // hay una posicion de puntero valida que respetar

  /* ---- escribir ------------------------------------------------------- */

  function mirar(v, x, y) {
    if (v.x === x && v.y === y) return;
    v.x = x;
    v.y = y;
    v.el.style.setProperty('--habi-mirada-x', x.toFixed(3));
    v.el.style.setProperty('--habi-mirada-y', y.toFixed(3));
  }

  function alFrente() {
    for (var i = 0; i < vivos.length; i++) {
      var v = vivos[i];
      v.x = null;
      v.y = null;
      v.el.style.removeProperty('--habi-mirada-x');
      v.el.style.removeProperty('--habi-mirada-y');
    }
  }

  // El rectangulo se cachea porque leerlo en cada movimiento del raton fuerza
  // un calculo de estilo por evento; se invalida al hacer scroll o redimensionar,
  // que son las dos cosas que lo mueven.
  function olvidarMedidas() {
    for (var i = 0; i < vivos.length; i++) vivos[i].medida = null;
    if (siguiendo) apuntar(ultimoX, ultimoY);
  }

  /* ---- seguir al puntero ---------------------------------------------- */

  function apuntar(px, py) {
    for (var i = 0; i < vivos.length; i++) {
      var v = vivos[i];
      if (!v.medida) v.medida = v.caja.getBoundingClientRect();
      var m = v.medida;
      var alcance = Math.max(m.width, m.height) * ALCANCE;
      if (!(alcance > 0)) continue;
      var x = (px - (m.left + m.width / 2)) / alcance;
      var y = (py - (m.top + m.height / 2)) / alcance;
      // Recorte radial: la mirada se queda dentro de la elipse del radio, en vez
      // de llegar mas lejos en diagonal que en recto.
      var largo = Math.sqrt(x * x + y * y);
      if (largo > 1) { x /= largo; y /= largo; }
      mirar(v, x, y);
    }
  }

  function alMover(e) {
    // Un dedo no es un cursor: un toque no debe congelar la mirada donde cayo.
    if (e.pointerType === 'touch') return;
    siguiendo = true;
    ultimoX = e.clientX;
    ultimoY = e.clientY;
    pararDeriva();
    apuntar(ultimoX, ultimoY);
  }

  function alSalir(e) {
    // Solo cuando el puntero abandona la ventana entera: mouseleave sobre <html>
    // no burbujea, asi que no lo disparan los cruces entre elementos de dentro.
    if (e && e.relatedTarget) return;
    siguiendo = false;
    arrancarDeriva();
  }

  /* ---- deriva ---------------------------------------------------------- */

  function eje(d, t) {
    return d.a1 * Math.sin((2 * Math.PI * t) / d.p1 + d.fase) +
           d.a2 * Math.sin((2 * Math.PI * t) / d.p2 + d.fase * 2);
  }

  function derivar(t) {
    cuadro = window.requestAnimationFrame(derivar);
    if (t - ultimoCuadro < PASO_DERIVA) return;
    ultimoCuadro = t;
    var x = eje(DERIVA[0], t);
    var y = eje(DERIVA[1], t);
    var largo = Math.sqrt(x * x + y * y);
    if (largo > 1) { x /= largo; y /= largo; }
    for (var i = 0; i < vivos.length; i++) mirar(vivos[i], x, y);
  }

  function arrancarDeriva() {
    if (!encendido || cuadro) return;
    ultimoCuadro = -Infinity;
    cuadro = window.requestAnimationFrame(derivar);
  }

  function pararDeriva() {
    if (!cuadro) return;
    window.cancelAnimationFrame(cuadro);
    cuadro = 0;
  }

  /* ---- encendido y apagado --------------------------------------------- */

  function encender() {
    if (encendido) return;
    encendido = true;
    window.addEventListener(puntero, alMover, pasivo);
    document.documentElement.addEventListener('mouseleave', alSalir, pasivo);
    window.addEventListener('blur', alSalir, pasivo);
    window.addEventListener('scroll', olvidarMedidas, pasivo);
    window.addEventListener('resize', olvidarMedidas, pasivo);
    arrancarDeriva();
  }

  function apagar() {
    if (!encendido) return;
    encendido = false;
    window.removeEventListener(puntero, alMover, pasivo);
    document.documentElement.removeEventListener('mouseleave', alSalir, pasivo);
    window.removeEventListener('blur', alSalir, pasivo);
    window.removeEventListener('scroll', olvidarMedidas, pasivo);
    window.removeEventListener('resize', olvidarMedidas, pasivo);
    pararDeriva();
    siguiendo = false;
    alFrente();
  }

  function repasar() {
    if (menos.matches) apagar(); else encender();
  }

  if (menos.addEventListener) menos.addEventListener('change', repasar);
  else if (menos.addListener) menos.addListener(repasar);
  repasar();
})();
