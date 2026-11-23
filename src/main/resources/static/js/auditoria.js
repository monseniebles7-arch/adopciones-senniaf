/* Vista de auditoria solo para el Administrador del sistema. */
(() => {
  const sesion = Sesion.exigir(['ADMINISTRADOR']);
  if (!sesion) return;
  Sesion.pintarBarra();

  const $ = id => document.getElementById(id);
  const form = $('filtros-auditoria');
  const filas = $('filas');
  const tamano = 25;
  let pagina = 0;
  let totalPaginas = 0;

  function celda(valor, clase) {
    const td = document.createElement('td');
    td.textContent = valor || '—';
    if (clase) td.className = clase;
    return td;
  }

  function parametros() {
    const p = new URLSearchParams({ pagina: String(pagina), tamano: String(tamano) });
    ['usuario', 'entidad', 'tipo', 'desde', 'hasta'].forEach(id => {
      const valor = $(id).value.trim();
      if (valor) p.set(id, valor);
    });
    return p;
  }

  async function cargar() {
    $('error').hidden = true;
    try {
      const r = await Sesion.pedir('/api/admin/auditoria?' + parametros().toString());
      if (!r.ok) {
        $('error-text').textContent = r.status === 403
          ? 'Esta sección es exclusiva del Administrador del sistema.'
          : 'No se pudo cargar la auditoría (' + r.status + ').';
        $('error').hidden = false;
        return;
      }

      const datos = await r.json();
      filas.replaceChildren();
      datos.contenido.forEach(ev => {
        const tr = document.createElement('tr');
        tr.append(
          celda(ev.fechaHora),
          celda(ev.usuario),
          celda(ev.rol),
          celda(ev.accion),
          celda({ EXPEDIENTE: 'Expediente', FAMILIA: 'Solicitud de familia', USUARIO: 'Cuenta de usuario' }[ev.entidad]),
          celda(ev.referencia, 'referencia')
        );
        filas.appendChild(tr);
      });

      totalPaginas = datos.totalPaginas;
      $('resumen').textContent = datos.total === 1 ? '1 evento registrado' : datos.total + ' eventos registrados';
      $('vacio').hidden = datos.contenido.length > 0;
      $('pagina-label').textContent = totalPaginas === 0 ? 'Sin páginas' : 'Página ' + (pagina + 1) + ' de ' + totalPaginas;
      $('anterior').disabled = pagina <= 0;
      $('siguiente').disabled = pagina + 1 >= totalPaginas;
    } catch (err) {
      if (err.message !== 'sesion') {
        $('error-text').textContent = 'No se pudo conectar con el servidor.';
        $('error').hidden = false;
      }
    }
  }

  form.addEventListener('submit', e => { e.preventDefault(); pagina = 0; cargar(); });
  $('limpiar').addEventListener('click', () => { form.reset(); pagina = 0; cargar(); });
  $('anterior').addEventListener('click', () => { if (pagina > 0) { pagina--; cargar(); } });
  $('siguiente').addEventListener('click', () => { if (pagina + 1 < totalPaginas) { pagina++; cargar(); } });
  cargar();
})();
