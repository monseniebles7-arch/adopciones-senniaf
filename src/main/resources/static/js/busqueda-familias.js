/* =====================================================================
   SENNIAF · busqueda-familias.js  (Sprint 9 · HU-08.1)

   En la ficha de un menor Declarado adoptable, el Equipo Técnico busca
   familias idóneas que acepten su edad:
   GET /api/tecnico/expedientes/{n}/familias-compatibles

   Las reglas las aplica el servidor; aquí solo se muestra el bloque cuando
   tiene sentido y se pinta el resultado. Todo con textContent.
   ===================================================================== */
(() => {
  const $ = id => document.getElementById(id);
  const numero = new URLSearchParams(location.search).get('n');

  function celda(fila, contenido) {
    const td = document.createElement('td');
    if (contenido instanceof Node) td.appendChild(contenido);
    else td.textContent = contenido;
    fila.appendChild(td);
  }

  function resumen(texto) {
    const p = $('busqueda-resumen');
    p.textContent = texto;
    p.hidden = false;
  }

  function pintar(datos) {
    const filas = $('busqueda-filas');
    filas.replaceChildren();

    let texto = datos.criterio + ': ' + (datos.total === 0 ? 'ninguna coincide'
      : datos.total + (datos.total === 1 ? ' familia compatible' : ' familias compatibles')) +
      ' de ' + datos.familiasEnEspera + ' en la lista de espera.';
    if (datos.sinRangoDeEdad > 0) {
      texto += ' ' + datos.sinRangoDeEdad + (datos.sinRangoDeEdad === 1 ? ' familia no declaró' : ' familias no declararon') +
        ' el rango de edad que acepta y no se pudo comparar.';
    }
    resumen(texto);

    datos.familias.forEach(f => {
      const tr = document.createElement('tr');
      celda(tr, String(f.posicionListaEspera));
      const enlace = document.createElement('a');
      enlace.href = 'familia_detalle.html?n=' + encodeURIComponent(f.numeroSolicitud);
      enlace.textContent = f.numeroSolicitud;
      celda(tr, enlace);
      celda(tr, f.familia);
      celda(tr, f.rangoEdad);
      celda(tr, f.fechaIdoneidad + ' (' + f.diasEnEspera + ' días)');
      filas.appendChild(tr);
    });
    $('busqueda-tabla').hidden = datos.total === 0;
  }

  $('btn-buscar-familias').addEventListener('click', async () => {
    const boton = $('btn-buscar-familias');
    boton.disabled = true;
    boton.textContent = 'Buscando…';
    try {
      const r = await Sesion.pedir('/api/tecnico/expedientes/' + encodeURIComponent(numero) + '/familias-compatibles');
      const datos = await r.json().catch(() => ({}));
      if (r.ok) pintar(datos);
      else {
        $('busqueda-tabla').hidden = true;
        resumen(datos.error || 'No se pudo hacer la búsqueda (' + r.status + ').');
      }
    } catch (err) {
      if (err.message !== 'sesion') resumen('No se pudo conectar con el servidor.');
    } finally {
      boton.disabled = false;
      boton.textContent = 'Buscar familias compatibles';
    }
  });

  document.addEventListener('expediente:cargado', ev => {
    const e = ev.detail;
    const sesion = Sesion.datos();
    $('bloque-busqueda').hidden = !(sesion && sesion.rol === 'TECNICO' &&
      e.estadoJuridico === 'DECLARADO_ADOPTABLE' && e.estado !== 'CERRADO');
  });
})();
