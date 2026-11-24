/* =====================================================================
   SENNIAF · historial-juridico.js  (Sprint 9 · HU-07.4)

   Cuando expediente-detalle.js termina de cargar la ficha (evento
   "expediente:cargado"), pide GET /api/expedientes/{n}/historial-juridico
   y pinta la línea de tiempo legal del menor: del ingreso a la etapa
   actual, con la observación de cada cambio y su resolución judicial.

   La ven el Equipo Técnico y el Juez. Todo con textContent.
   ===================================================================== */
(() => {
  const numero = new URLSearchParams(location.search).get('n');
  const ROL = { ADMINISTRADOR: 'Administrador', TECNICO: 'Equipo Técnico', JUEZ: 'Juez' };

  function linea(texto, clase) {
    const p = document.createElement('p');
    if (clase) p.className = clase;
    p.textContent = texto;
    return p;
  }

  /* El documento se pide con el token y se descarga desde el blob. */
  async function descargar(res) {
    try {
      const r = await Sesion.pedir('/api/expedientes/' + encodeURIComponent(numero) +
        '/documentos/' + res.documentoId);
      if (!r.ok) return;
      const url = URL.createObjectURL(await r.blob());
      const a = document.createElement('a');
      a.href = url;
      a.download = res.documentoNombre;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (err) { /* la sesión vencida ya redirige al login */ }
  }

  function pintar(datos) {
    const lista = document.getElementById('linea-juridica');
    lista.replaceChildren();

    datos.linea.forEach((l, i) => {
      const li = document.createElement('li');
      if (i === datos.linea.length - 1) li.className = 'actual';

      const titulo = document.createElement('p');
      titulo.className = 'etapa-titulo';
      const pill = document.createElement('span');
      pill.className = 'pill etapa ' + String(l.etapa).toLowerCase();
      pill.textContent = l.etapaEtiqueta;
      titulo.append(l.etapaAnteriorEtiqueta ? 'De ' + l.etapaAnteriorEtiqueta + ' a ' : 'Inicio: ', pill);
      li.appendChild(titulo);

      if (l.observacion) li.appendChild(linea(l.observacion));

      if (l.resolucion) {
        const r = l.resolucion;
        const caja = document.createElement('div');
        caja.className = 'resolucion-dato';
        caja.appendChild(linea('Resolución N.º ' + r.numero + ' del ' + r.fecha + ' · ' + r.juzgado));
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.className = 'enlace';
        boton.textContent = 'Descargar ' + r.documentoNombre;
        boton.addEventListener('click', () => descargar(r));
        caja.appendChild(boton);
        li.appendChild(caja);
      } else if (l.sinResolucionPrevia) {
        li.appendChild(linea('Sin resolución registrada: este cambio es anterior a la resolución obligatoria (Sprint 9).',
          'aviso-resolucion'));
      }

      li.appendChild(linea((l.usuario || '—') + (l.rol ? ' (' + (ROL[l.rol] || l.rol) + ')' : '') +
        ' · ' + l.fechaHora, 'firma'));
      lista.appendChild(li);
    });

    document.getElementById('bloque-historial-juridico').hidden = false;
  }

  document.addEventListener('expediente:cargado', async () => {
    const sesion = Sesion.datos();
    if (!sesion || (sesion.rol !== 'TECNICO' && sesion.rol !== 'JUEZ')) return;
    try {
      const r = await Sesion.pedir('/api/expedientes/' + encodeURIComponent(numero) + '/historial-juridico');
      if (r.ok) pintar(await r.json());
    } catch (err) { /* sin historial jurídico no se bloquea el resto de la ficha */ }
  });
})();
