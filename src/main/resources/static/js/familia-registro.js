/* =====================================================================
   SENNIAF · familia-registro.js  (HU-05)
   Validación del formulario y envío a POST /api/tecnico/familias.

   La validación de aquí es comodidad: marca los errores mientras la
   persona escribe para que no descubra diez problemas al final. La que
   decide es la del servidor, en FamiliaService.validar(), porque esta
   se salta con cualquier herramienta de línea de comandos.
   ===================================================================== */
(() => {
  const publico = new URLSearchParams(location.search).get('publico') === '1';
  const sesion = publico ? null : Sesion.exigir(['TECNICO']);
  if (!publico && !sesion) return;
  if (!publico) Sesion.pintarBarra();

  if (publico) {
    document.querySelector('.topbar .user').hidden = true;
    document.querySelector('.topbar .brand').innerHTML = '<a href="index.html" style="color:inherit;text-decoration:none">SENNIAF</a><small>Solicitud de familia</small>';
    document.querySelector('a.btn-secondary').href = 'index.html';
    document.querySelector('a.btn-secondary').textContent = 'Volver al inicio';
    document.querySelector('[name="declaracion_trazabilidad"]').closest('label').querySelector('span').innerHTML = 'Entiendo que el sistema registrará la fecha y hora de envío de esta solicitud. <span class="req" aria-hidden="true">*</span>';
  }

  const form = document.getElementById('form-familia');
  const bannerOk = document.getElementById('banner-creada');
  const bannerError = document.getElementById('banner-error');
  const textoError = document.getElementById('banner-error-text');
  let tokenFormulario = null;

  async function renovarTokenFormulario() {
    if (!publico) return;
    tokenFormulario = null;
    try {
      const respuesta = await fetch('/api/solicitudes/familias/token', { cache: 'no-store' });
      if (!respuesta.ok) return;
      tokenFormulario = (await respuesta.json()).token || null;
    } catch (err) {
      tokenFormulario = null;
    }
  }

  /* Al volver tras registrar, con el número en la URL */
  const creada = new URLSearchParams(location.search).get('creada');
  if (creada) {
    document.getElementById('numero-creado').textContent = creada;
    const parametros = new URLSearchParams(location.search);
    if (publico) {
      document.getElementById('mensaje-creada').textContent = 'Tu solicitud fue recibida. Número de solicitud:';
      document.getElementById('mensaje-asignacion').textContent = parametros.get('pendiente') === '1'
        ? 'Está guardada y pendiente de asignación; se asignará automáticamente cuando haya un Técnico disponible.'
        : 'Un Técnico del equipo quedó a cargo de atenderla.';
    } else {
      document.getElementById('mensaje-creada').textContent = 'Solicitud registrada correctamente. Número:';
    }
    bannerOk.hidden = false;
  }

  const $ = s => form.querySelector(s);
  const val = n => { const e = form.elements[n]; return e ? String(e.value || '').trim() : ''; };

  // ------------------------------------------------------------------
  //  Campos que aparecen solo cuando aplican
  // ------------------------------------------------------------------
  const CONDICIONES = [
    ['#g-segundo', () => val('tiene_segundo') === 'si'],
    ['#g-hijos', () => val('tiene_hijos') === 'si'],
    ['#g-detalle-aceptacion', () => val('acepta_necesidades') === 'si']
  ];

  function aplicarCondiciones() {
    CONDICIONES.forEach(([sel, mostrar]) => {
      const g = $(sel);
      if (!g) return;                     // la guarda que faltaba en el otro formulario
      const on = mostrar();
      g.hidden = !on;
      g.querySelectorAll('input, select, textarea').forEach(el => {
        el.disabled = !on;
        /* Deshabilitado no viaja en el envío: minimización de datos.
           Al ocultarlo se limpia para no mandar un resto de antes. */
        if (!on) { el.value = ''; marcar(el, ''); }
      });
    });
  }

  // ------------------------------------------------------------------
  //  Validación
  // ------------------------------------------------------------------
  function anios(iso) {
    if (!iso) return null;
    const n = new Date(iso + 'T00:00:00');
    if (isNaN(n)) return null;
    const hoy = new Date();
    let a = hoy.getFullYear() - n.getFullYear();
    const m = hoy.getMonth() - n.getMonth();
    if (m < 0 || (m === 0 && hoy.getDate() < n.getDate())) a--;
    return a;
  }

  /** Mensaje de error de un campo, o '' si está bien. */
  function revisar(el) {
    if (el.disabled || el.type === 'hidden') return '';

    const vacio = el.type === 'checkbox' ? !el.checked : !String(el.value).trim();

    // Obligatorio: en el HTML con required, o porque su sección está activa
    const obligatorioPorSeccion =
      (val('tiene_segundo') === 'si' &&
        ['segundo_nombres', 'segundo_apellidos', 'segundo_tipo_documento',
         'segundo_numero_documento', 'segundo_fecha_nacimiento'].indexOf(el.name) !== -1) ||
      (val('tiene_hijos') === 'si' && el.name === 'hijos_cantidad') ||
      (val('acepta_necesidades') === 'si' && el.name === 'detalle_aceptacion') ||
      el.name.indexOf('declaracion_') === 0;

    if ((el.required || obligatorioPorSeccion) && vacio) {
      return el.type === 'checkbox' ? 'Debes marcar esta casilla.' : 'Este campo es obligatorio.';
    }
    if (vacio) return '';

    if (el.name === 'fecha_nacimiento' || el.name === 'segundo_fecha_nacimiento') {
      const a = anios(el.value);
      if (a === null) return 'La fecha no es válida.';
      if (a < 0) return 'La fecha no puede estar en el futuro.';
      if (a < 18) return 'El solicitante debe ser mayor de edad.';
      if (a > 110) return 'Revisa la fecha: la edad resultante no es razonable.';
    }

    if (el.name === 'correo' && !/^[^@\s]+@[^@\s]+\.[^@\s]{2,}$/.test(el.value.trim())) {
      return 'El correo no tiene un formato válido.';
    }

    if (el.name === 'edad_minima' || el.name === 'edad_maxima') {
      const n = Number(el.value);
      if (!Number.isInteger(n) || n < 0 || n > 17) return 'Debe ser un número entre 0 y 17.';
    }

    if (el.name === 'habitaciones' || el.name === 'personas_hogar' || el.name === 'hijos_cantidad') {
      const n = Number(el.value);
      if (!Number.isInteger(n) || n < 1) return 'Debe ser un número mayor que cero.';
    }

    /* Dos solicitantes no pueden ser la misma persona: pasa cuando se
       copia y pega la cédula del primero por descuido. */
    if (el.name === 'segundo_numero_documento'
        && el.value.trim().toLowerCase() === val('numero_documento').toLowerCase()) {
      return 'Los dos solicitantes no pueden tener el mismo documento.';
    }

    return '';
  }

  function marcar(el, msg) {
    const campo = el.closest('.field');
    const salida = document.getElementById('e-' + el.name);
    if (campo) campo.classList.toggle('invalid', !!msg);
    if (salida) salida.textContent = msg || '';
  }

  /** El rango se valida como un todo: el error va en el campo de la derecha. */
  function revisarRango() {
    const min = form.edad_minima.value, max = form.edad_maxima.value;
    if (min === '' || max === '') return '';
    if (Number(min) > Number(max)) {
      return 'La edad mínima no puede ser mayor que la máxima.';
    }
    return '';
  }

  function campos() {
    return Array.from(form.querySelectorAll('input, select, textarea'))
      .filter(el => !el.disabled && el.type !== 'hidden');
  }

  function pintarEdad() {
    const salida = document.getElementById('edad-out');
    const a = anios(form.fecha_nacimiento.value);
    salida.textContent = (a === null || a < 0) ? '—' : a + ' años';
  }

  // ------------------------------------------------------------------
  //  Eventos
  // ------------------------------------------------------------------
  form.addEventListener('change', e => {
    aplicarCondiciones();
    if (e.target.name === 'fecha_nacimiento') pintarEdad();
    marcar(e.target, revisar(e.target));
    if (e.target.name === 'edad_minima' || e.target.name === 'edad_maxima') {
      const msg = revisar(form.edad_maxima) || revisarRango();
      marcar(form.edad_maxima, msg);
    }
  });

  form.addEventListener('input', e => {
    /* Solo se vuelve a marcar lo que ya estaba en rojo: ir corrigiendo
       a alguien mientras todavía escribe es molesto. */
    if (e.target.closest('.invalid')) marcar(e.target, revisar(e.target));
  });

  form.addEventListener('submit', e => {
    e.preventDefault();

    let malos = 0, primero = null;
    campos().forEach(el => {
      const msg = revisar(el);
      marcar(el, msg);
      if (msg) { malos++; if (!primero) primero = el; }
    });

    const msgRango = revisarRango();
    if (msgRango) {
      marcar(form.edad_maxima, msgRango);
      malos++;
      if (!primero) primero = form.edad_maxima;
    }

    if (malos > 0) {
      bannerOk.hidden = true;
      textoError.textContent = malos === 1
        ? 'Hay un campo con un problema. Está marcado más abajo.'
        : 'Hay ' + malos + ' campos con problemas. Están marcados más abajo.';
      bannerError.hidden = false;
      bannerError.scrollIntoView({ block: 'start' });
      if (primero) primero.focus({ preventScroll: true });
      return;
    }

    enviar();
  });

  // ------------------------------------------------------------------
  //  Envío
  // ------------------------------------------------------------------
  function fallar(msg) {
    textoError.textContent = msg;
    bannerError.hidden = false;
    bannerError.scrollIntoView({ block: 'start' });
    const b = document.getElementById('btn-guardar');
    b.disabled = false;
    b.textContent = 'Registrar solicitud';
  }

  async function enviar() {
    bannerError.hidden = true;
    bannerOk.hidden = true;

    const boton = document.getElementById('btn-guardar');
    boton.disabled = true;
    boton.textContent = 'Registrando…';

    /* Los campos deshabilitados no entran en FormData, así que no se
       envían. Las casillas se agregan aparte porque FormData omite las
       que no están marcadas y el servidor necesita el false explícito. */
    const datos = {};
    new FormData(form).forEach((v, k) => { datos[k] = v; });
    form.querySelectorAll('input[type=checkbox]').forEach(c => { datos[c.name] = c.checked; });
    if (publico) {
      if (!tokenFormulario) {
        fallar('No se pudo preparar el formulario protegido. Recarga la página e inténtalo nuevamente.');
        boton.disabled = false;
        boton.textContent = 'Registrar solicitud';
        return;
      }
      datos.form_token = tokenFormulario;
    }

    try {
      const r = await Sesion.pedir(publico ? '/api/solicitudes/familias' : '/api/tecnico/familias', {
        method: 'POST',
        body: JSON.stringify(datos)
      });

      if (!r.ok && publico) await renovarTokenFormulario();
      if (r.status === 403) { fallar('Tu rol no tiene permiso para registrar familias. La solicitud no se guardó.'); return; }
      if (r.status === 409) {
        const detalle = await r.json().catch(() => ({}));
        fallar(detalle.error || 'Ya existe una solicitud con uno de esos documentos. Contacta al equipo técnico para actualizarla.');
        marcar(form.numero_documento, 'Documento ya registrado en otra solicitud.');
        if (form.segundo_numero_documento && !form.segundo_numero_documento.disabled) {
          marcar(form.segundo_numero_documento, 'Documento ya registrado en otra solicitud.');
        }
        return;
      }
      if (r.status === 429) {
        const segundos = Number(r.headers.get('Retry-After')) || 0;
        fallar('Se alcanzó el límite temporal de envíos desde esta conexión. Intenta nuevamente en ' + segundos + ' segundos.');
        return;
      }
      if (r.status === 400) {
        const detalle = await r.json().catch(() => ({}));
        const lista = (detalle.campos || []).join(', ');
        fallar(detalle.error || ('El servidor rechazó los datos' + (lista ? ': ' + lista : '') + '.'));
        return;
      }
      if (!r.ok) { fallar('El servidor respondió con un error (' + r.status + '). La solicitud no se guardó.'); return; }

      const familia = await r.json();
      /* Se recarga con el número para que el formulario quede limpio
         para el siguiente registro. */
      const consulta = new URLSearchParams();
      if (publico) {
        consulta.set('publico', '1');
        if (familia.asignacionPendiente) consulta.set('pendiente', '1');
      }
      consulta.set('creada', familia.numeroSolicitud);
      location.href = 'familia_registro.html?' + consulta.toString();

    } catch (err) {
      await renovarTokenFormulario();
      if (err.message !== 'sesion') fallar('No se pudo conectar con el servidor. La solicitud no se guardó.');
    }
  }

  aplicarCondiciones();
  pintarEdad();
  renovarTokenFormulario();
})();
