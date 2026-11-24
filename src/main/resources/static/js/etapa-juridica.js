/* =====================================================================
   SENNIAF · etapa-juridica.js  (Sprint 8 · HU-07.2 y reapertura)

   Espera a que expediente-detalle.js termine de cargar la ficha (evento
   "expediente:cargado") y arma:
     · el bloque "Etapa jurídica", que solo ve el Juez;
     · el formulario "Reabrir expediente", solo para el Equipo Técnico.

   Las reglas las decide el servidor; aquí solo se ofrecen las opciones
   posibles y se avisa antes de enviar. Todo con textContent.
   ===================================================================== */
(() => {
  const MINIMO = 20;   // igual que en el servidor
  const ETAPA = {
    EN_PROTECCION: 'En protección',
    EN_PROCESO_JUDICIAL: 'En proceso judicial',
    DECLARADO_ADOPTABLE: 'Declarado adoptable',
    ADOPTADO: 'Adoptado',
    REINTEGRADO: 'Reintegrado a su familia'
  };

  const $ = id => document.getElementById(id);
  const numero = new URLSearchParams(location.search).get('n');
  const banner = $('banner-error');
  const textoBanner = $('banner-error-text');

  function fallar(msg) {
    textoBanner.textContent = msg;
    banner.hidden = false;
    banner.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  function marcar(campo, msg) {
    const salida = $('e-' + campo);
    if (salida) salida.textContent = msg || '';
    const control = {
      etapa: 'nueva-etapa', observacion: 'obs-etapa', motivo: 'motivo-reapertura',
      resolucion_numero: 'res-numero', resolucion_fecha: 'res-fecha',
      resolucion_juzgado: 'res-juzgado', archivo: 'res-archivo'
    }[campo];
    const caja = control && $(control).closest('.field');
    if (caja) caja.classList.toggle('invalid', !!msg);
  }

  /* ------------------------ HU-07.2 · Juez ------------------------ */

  function prepararEtapa(e) {
    const sesion = Sesion.datos();
    if (!sesion || sesion.rol !== 'JUEZ') return;

    const bloque = $('bloque-etapa');
    const nota = $('etapa-nota');
    const form = $('form-etapa');
    bloque.hidden = false;
    nota.hidden = true;
    form.hidden = true;

    if (e.estado === 'CERRADO') {
      nota.textContent = 'El expediente está cerrado: el Equipo Técnico debe reabrirlo antes de cambiar la etapa jurídica.';
      nota.hidden = false;
      return;
    }
    if (e.etapaFinal) {
      nota.textContent = (ETAPA[e.estadoJuridico] || e.estadoJuridico) + ' es una etapa final: ya no admite cambios.';
      nota.hidden = false;
      return;
    }

    // Solo se ofrecen las etapas a las que se puede pasar desde la actual.
    const select = $('nueva-etapa');
    select.replaceChildren();
    const vacia = document.createElement('option');
    vacia.value = '';
    vacia.textContent = 'Selecciona';
    select.appendChild(vacia);
    (e.siguientesEtapas || []).forEach(v => {
      const o = document.createElement('option');
      o.value = v;
      o.textContent = ETAPA[v] || v;
      select.appendChild(o);
    });
    if ((e.siguientesEtapas || []).length === 1) select.value = e.siguientesEtapas[0];
    // La fecha de la resolución no puede ser futura.
    $('res-fecha').max = new Date().toISOString().slice(0, 10);
    mostrarResolucion();
    form.hidden = false;
  }

  /* Sprint 9 · HU-07.3: la resolución solo se pide para Declarado adoptable. */
  const ADOPTABLE = 'DECLARADO_ADOPTABLE';
  function mostrarResolucion() {
    const pide = $('nueva-etapa').value === ADOPTABLE;
    $('bloque-resolucion').hidden = !pide;
    $('btn-etapa').textContent = pide ? 'Declarar adoptable' : 'Cambiar etapa';
  }
  $('nueva-etapa').addEventListener('change', mostrarResolucion);

  function validarEtapa() {
    let ok = true;
    if (!$('nueva-etapa').value) { marcar('etapa', 'Elige la nueva etapa.'); ok = false; }
    else marcar('etapa', '');
    if ($('obs-etapa').value.trim().length < MINIMO) {
      marcar('observacion', 'Explica el cambio en al menos ' + MINIMO + ' caracteres.'); ok = false;
    } else marcar('observacion', '');

    if ($('nueva-etapa').value === ADOPTABLE) {
      const revisar = (campo, falta, msg) => { marcar(campo, falta ? msg : ''); if (falta) ok = false; };
      revisar('resolucion_numero', $('res-numero').value.trim().length < 3, 'Escribe el número de la resolución.');
      const fecha = $('res-fecha').value;
      revisar('resolucion_fecha', !fecha || fecha > $('res-fecha').max,
        'Indica la fecha de la resolución; no puede ser futura.');
      revisar('resolucion_juzgado', $('res-juzgado').value.trim().length < 3, 'Indica el juzgado que la emitió.');
      revisar('archivo', !$('res-archivo').files.length, 'Adjunta el documento de la resolución.');
    }
    return ok;
  }

  /* Declarado adoptable va con la resolución y su documento (multipart). */
  function enviarCambio() {
    const base = '/api/juez/expedientes/' + encodeURIComponent(numero);
    if ($('nueva-etapa').value === ADOPTABLE) {
      const datos = new FormData();
      datos.append('observacion', $('obs-etapa').value.trim());
      datos.append('resolucion_numero', $('res-numero').value.trim());
      datos.append('resolucion_fecha', $('res-fecha').value);
      datos.append('resolucion_juzgado', $('res-juzgado').value.trim());
      datos.append('archivo', $('res-archivo').files[0]);
      return Sesion.pedir(base + '/declaracion-adoptabilidad', { method: 'POST', body: datos });
    }
    return Sesion.pedir(base + '/estado-juridico', {
      method: 'PATCH',
      body: JSON.stringify({ etapa: $('nueva-etapa').value, observacion: $('obs-etapa').value.trim() })
    });
  }

  $('form-etapa').addEventListener('submit', async ev => {
    ev.preventDefault();
    banner.hidden = true;
    if (!validarEtapa()) return;

    const boton = $('btn-etapa');
    boton.disabled = true;
    boton.textContent = 'Guardando…';
    try {
      const r = await enviarCambio();
      if (r.ok) { location.reload(); return; }
      const detalle = await r.json().catch(() => ({}));
      if (r.status === 403) fallar('Solo el Juez puede cambiar la etapa jurídica.');
      else if (r.status === 400) {
        fallar(detalle.error || 'El cambio no cumple las reglas.');
        (detalle.campos || []).forEach(c => marcar(c, detalle.error));
      } else fallar(detalle.error || 'No se pudo cambiar la etapa (' + r.status + ').');
    } catch (err) {
      if (err.message !== 'sesion') fallar('No se pudo conectar con el servidor.');
    } finally {
      boton.disabled = false;
      mostrarResolucion();
    }
  });
  ['nueva-etapa', 'obs-etapa', 'res-numero', 'res-fecha', 'res-juzgado', 'res-archivo'].forEach(id => $(id).addEventListener('input', () => {
    if ($('form-etapa').querySelector('.invalid')) validarEtapa();
  }));

  /* ------------------ Reapertura · Equipo Técnico ------------------ */

  const abrir = $('btn-abrir-reapertura');
  const formReap = $('form-reapertura');
  abrir.addEventListener('click', () => {
    abrir.hidden = true;
    formReap.hidden = false;
    $('motivo-reapertura').focus();
  });
  $('btn-cancelar-reapertura').addEventListener('click', () => {
    formReap.hidden = true;
    abrir.hidden = false;
  });
  formReap.addEventListener('submit', async ev => {
    ev.preventDefault();
    banner.hidden = true;
    const motivo = $('motivo-reapertura').value.trim();
    if (motivo.length < MINIMO) {
      marcar('motivo', 'Explica el motivo en al menos ' + MINIMO + ' caracteres.');
      return;
    }
    marcar('motivo', '');
    const boton = $('btn-reabrir');
    boton.disabled = true;
    boton.textContent = 'Reabriendo…';
    try {
      const r = await Sesion.pedir('/api/tecnico/expedientes/' + encodeURIComponent(numero) + '/reapertura',
        { method: 'POST', body: JSON.stringify({ motivo }) });
      if (r.ok) { location.reload(); return; }
      const detalle = await r.json().catch(() => ({}));
      if (r.status === 403) fallar('Solo el Equipo Técnico puede reabrir un expediente.');
      else fallar(detalle.error || 'No se pudo reabrir el expediente (' + r.status + ').');
    } catch (err) {
      if (err.message !== 'sesion') fallar('No se pudo conectar con el servidor.');
    } finally {
      boton.disabled = false;
      boton.textContent = 'Reabrir expediente';
    }
  });

  document.addEventListener('expediente:cargado', e => prepararEtapa(e.detail));
})();
