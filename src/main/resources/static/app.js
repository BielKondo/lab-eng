/* Front-end do ResgatAI — integração inicial com a API (/api). Sem framework, só fetch. */
(function () {
  'use strict';

  const API = '/api';
  let usuario = null;          // usuário identificado na tela "Entrar"
  let loteSelecionado = null;  // lote aberto na tela "Oferta"

  // ---------- utilidades ----------
  const $ = (sel) => document.querySelector(sel);
  const $$ = (sel) => Array.from(document.querySelectorAll(sel));
  const fmtData = (iso) => iso ? new Date(iso).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) : '-';
  const fmtPreco = (l) => l.doacao ? 'Doação' : 'R$ ' + Number(l.precoReduzido).toFixed(2).replace('.', ',');
  const fmtKm = (km) => km == null ? '' : (km < 1 ? Math.round(km * 1000) + ' m' : km.toFixed(1).replace('.', ',') + ' km');
  const escapar = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const paraInstant = (local) => local ? new Date(local).toISOString() : null;
  const numeroOuNulo = (v) => v === '' || v == null ? null : Number(v);
  const soDigitos = (doc) => String(doc).replace(/\D/g, ''); // nos caminhos da API o documento vai sem pontuação

  function toast(msg) {
    const t = $('#toast');
    t.textContent = msg;
    t.classList.remove('oculto');
    clearTimeout(t._timer);
    t._timer = setTimeout(() => t.classList.add('oculto'), 4000);
  }

  function lembrar(chave, valor) { try { valor == null ? localStorage.removeItem(chave) : localStorage.setItem(chave, valor); } catch (e) { /* armazenamento indisponível */ } }
  function lembrado(chave) { try { return localStorage.getItem(chave); } catch (e) { return null; } }

  async function api(caminho, opcoes = {}) {
    const cabecalhos = { 'Content-Type': 'application/json' };
    if (usuario) cabecalhos['X-Documento'] = usuario.documento;
    if (opcoes.documento) cabecalhos['X-Documento'] = opcoes.documento;
    const resposta = await fetch(API + caminho, { method: opcoes.method || 'GET', headers: cabecalhos,
      body: opcoes.body ? JSON.stringify(opcoes.body) : undefined });
    const texto = await resposta.text();
    let corpo = null;
    try { corpo = texto ? JSON.parse(texto) : null; } catch (e) { corpo = null; } // erro fora da API (ex.: página HTML do servidor)
    if (!resposta.ok) {
      const erro = new Error((corpo && corpo.erro) || ('Erro ' + resposta.status));
      erro.status = resposta.status;
      erro.detalhes = corpo && corpo.detalhes;
      throw erro;
    }
    return corpo;
  }

  /** Mostra o erro e, no caso de dados inválidos (6a/9a), marca cada campo apontado mantendo o que foi digitado. */
  function mostrarErro(form, elErro, erro) {
    form && $$('.invalido', form).forEach((e) => e.classList.remove('invalido'));
    let texto = erro.message;
    if (erro.detalhes && typeof erro.detalhes === 'object' && !Array.isArray(erro.detalhes) && !erro.detalhes.id) {
      texto += '\n' + Object.entries(erro.detalhes).map(([campo, msg]) => '• ' + campo + ': ' + msg).join('\n');
      if (form) Object.keys(erro.detalhes).forEach((campo) => { const el = form.elements[campo]; if (el) el.classList.add('invalido'); });
    }
    elErro.textContent = texto;
  }

  function mostrarTela(id) {
    $$('.tela').forEach((t) => t.classList.add('oculto'));
    $('#' + id).classList.remove('oculto');
    $$('.abas button').forEach((b) => b.classList.toggle('ativa', b.dataset.tela === id));
    window.scrollTo(0, 0);
    const carregar = { 'tela-ofertas': carregarOfertas, 'tela-notificacoes': carregarNotificacoes, 'tela-reservas': carregarReservas,
      'tela-meus-lotes': carregarMeusLotes, 'tela-cadastrar-lote': prepararFormLote, 'tela-usuarios': carregarUsuarios }[id];
    if (carregar) carregar();
  }
  document.addEventListener('click', (ev) => {
    const alvo = ev.target.closest('[data-tela], [data-voltar]');
    if (alvo) mostrarTela(alvo.dataset.tela || alvo.dataset.voltar);
  });

  // ---------- Entrar ----------
  $('#form-entrar').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const documento = $('#documento').value.trim();
    const perfil = $('#form-entrar').elements.perfil.value;
    try {
      const u = await api('/usuarios/eu', { documento });
      if (u.perfil !== perfil) throw new Error('Este documento pertence a um perfil ' + u.perfil.toLowerCase() + '.');
      if (!u.ativo) throw new Error('Cadastro inativo.');
      entrar(u);
    } catch (e) { mostrarErro(null, $('#erro-entrar'), e); }
  });

  function entrar(u) {
    usuario = u;
    lembrar('resgatai.documento', u.documento);
    $('#usuario-nome').textContent = u.nome + ' · ' + u.perfil.toLowerCase();
    $('#usuario-atual').classList.remove('oculto');
    $('#erro-entrar').textContent = '';
    if (u.perfil === 'RECEPTOR') {
      $('#aviso-aprovacao').classList.toggle('oculto', u.statusAnalise === 'APROVADA');
      mostrarTela('tela-ofertas');
    } else if (u.perfil === 'DOADOR') {
      mostrarTela('tela-cadastrar-lote');
    } else {
      mostrarTela('tela-usuarios');
    }
  }

  $('#btn-sair').addEventListener('click', () => {
    usuario = null;
    lembrar('resgatai.documento', null);
    $('#usuario-atual').classList.add('oculto');
    mostrarTela('tela-entrar');
  });

  // ---------- Receptor: ofertas (UC04-2a) ----------
  function cartaoLote(l, aoClicar) {
    const el = document.createElement('div');
    el.className = 'item' + (aoClicar ? '' : ' estatico');
    el.innerHTML = (l.foto ? `<img class="foto" src="${escapar(l.foto)}" alt="">` : '<div class="foto">sem foto</div>')
      + `<div><div class="titulo">${escapar(l.nome)} · ${l.quantidade} ${escapar(l.unidadeMedida)}</div>
         <div class="meta">${escapar(l.doador)}${l.distanciaKm != null ? ' · ' + fmtKm(l.distanciaKm) : ''} · vence ${fmtData(l.dataValidade)}
         <span class="tag ${l.urgencia}">${l.urgencia}</span> <span class="tag ${l.status}">${l.status}</span></div></div>
         <div class="preco">${fmtPreco(l)}</div>`;
    if (aoClicar) el.addEventListener('click', () => aoClicar(l));
    return el;
  }

  async function carregarOfertas() {
    const f = $('#form-busca');
    const params = new URLSearchParams();
    if (f.categoria.value) params.set('categoria', f.categoria.value);
    if (f.precoMaximo.value) params.set('precoMaximo', f.precoMaximo.value);
    if (f.raioKm.value) params.set('raioKm', f.raioKm.value);
    if (f.validadeAte.value) params.set('validadeAte', paraInstant(f.validadeAte.value));
    const lista = $('#lista-ofertas');
    lista.innerHTML = '<p class="sub">Carregando…</p>';
    try {
      const lotes = await api('/lotes/disponiveis?' + params);
      lista.innerHTML = lotes.length ? '' : '<p class="sub">Nenhum lote disponível com esses critérios.</p>';
      lotes.forEach((l) => lista.appendChild(cartaoLote(l, abrirOferta)));
      atualizarBadgeNotificacoes();
    } catch (e) { lista.innerHTML = `<p class="erro">${escapar(e.message)}</p>`; }
  }
  $('#form-busca').addEventListener('submit', (ev) => { ev.preventDefault(); carregarOfertas(); });

  // ---------- Receptor: oferta (UC04 passos 2-3) ----------
  function abrirOferta(l) {
    loteSelecionado = l;
    $('#detalhe-oferta').innerHTML = `
      ${l.foto ? `<img src="${escapar(l.foto)}" alt="" style="max-width:100%;border-radius:8px">` : ''}
      <h2>${escapar(l.nome)} <span class="tag ${l.urgencia}">${l.urgencia}</span></h2>
      <dl class="detalhe">
        <dt>Quantidade</dt><dd>${l.quantidade} ${escapar(l.unidadeMedida)}${l.categoria ? ' · ' + escapar(l.categoria) : ''}</dd>
        <dt>Preço</dt><dd>${fmtPreco(l)}</dd>
        <dt>Data de validade</dt><dd>${fmtData(l.dataValidade)}</dd>
        <dt>Janela de retirada</dt><dd>${fmtData(l.inicioRetirada)} até ${fmtData(l.fimRetirada)}</dd>
        <dt>Endereço de retirada</dt><dd>${escapar(l.enderecoRetirada)}${l.distanciaKm != null ? ' (' + fmtKm(l.distanciaKm) + ')' : ''}</dd>
        <dt>Doador</dt><dd>${escapar(l.doador)}</dd>
        ${l.condicoesConservacao ? `<dt>Conservação</dt><dd>${escapar(l.condicoesConservacao)}</dd>` : ''}
      </dl>`;
    $('#erro-oferta').textContent = '';
    $('#alternativas').classList.add('oculto');
    $('#btn-reservar').disabled = usuario.statusAnalise !== 'APROVADA';
    mostrarTela('tela-oferta');
  }

  // UC04, passo 4: reservar
  $('#btn-reservar').addEventListener('click', async () => {
    $('#erro-oferta').textContent = '';
    try {
      const r = await api('/reservas', { method: 'POST', body: { loteId: loteSelecionado.id } });
      mostrarConfirmacao(r);
    } catch (e) {
      mostrarErro(null, $('#erro-oferta'), e);
      if (e.status === 409 && Array.isArray(e.detalhes)) {   // 6a: já reservado → outras ofertas
        const lista = $('#lista-alternativas');
        lista.innerHTML = e.detalhes.length ? '' : '<p class="sub">Nenhuma outra oferta no momento.</p>';
        e.detalhes.forEach((l) => lista.appendChild(cartaoLote(l, abrirOferta)));
        $('#alternativas').classList.remove('oculto');
        $('#btn-reservar').disabled = true;
      }
      if (e.status === 410) $('#btn-reservar').disabled = true; // 6b: expirado
    }
  });

  // UC04, passo 11: confirmação com PIN/QR
  function mostrarConfirmacao(r) {
    const qr = $('#qr');
    qr.innerHTML = '';
    if (window.QRCode) {
      new QRCode(qr, { text: r.conteudoQrCode, width: 180, height: 180 });
    } else {
      qr.innerHTML = `<div class="qr-texto">${escapar(r.conteudoQrCode)}</div>`;
    }
    $('#pin').innerHTML = r.pin.split('').map((d) => `<span>${d}</span>`).join('');
    $('#prazo-retirada').textContent = 'Retirar até ' + fmtData(r.prazoLimiteRetirada);
    $('#endereco-retirada').textContent = r.lote.enderecoRetirada;
    mostrarTela('tela-confirmacao');
  }

  async function carregarReservas() {
    const lista = $('#lista-reservas');
    lista.innerHTML = '<p class="sub">Carregando…</p>';
    try {
      const reservas = await api('/reservas');
      lista.innerHTML = reservas.length ? '' : '<p class="sub">Você ainda não tem reservas.</p>';
      reservas.forEach((r) => {
        const el = cartaoLote(r.lote, () => mostrarConfirmacao(r));
        el.querySelector('.preco').innerHTML = `<span class="tag">${r.status}</span><br><small>PIN ${r.pin}</small>`;
        lista.appendChild(el);
      });
    } catch (e) { lista.innerHTML = `<p class="erro">${escapar(e.message)}</p>`; }
  }

  // ---------- Notificações (UC03 / UC04 passo 1) ----------
  function abasPara(perfil, ativa) {
    const abas = perfil === 'RECEPTOR'
      ? [['tela-ofertas', 'Ofertas perto'], ['tela-notificacoes', 'Notificações'], ['tela-reservas', 'Minhas reservas']]
      : [['tela-cadastrar-lote', 'Cadastrar lote'], ['tela-meus-lotes', 'Meus lotes'], ['tela-notificacoes', 'Notificações']];
    return abas.map(([id, rotulo]) => `<button data-tela="${id}" class="${id === ativa ? 'ativa' : ''}">${rotulo}</button>`).join('');
  }

  async function carregarNotificacoes() {
    $('#abas-notificacoes').innerHTML = abasPara(usuario.perfil, 'tela-notificacoes');
    const lista = $('#lista-notificacoes');
    lista.innerHTML = '<p class="sub">Carregando…</p>';
    try {
      const itens = await api('/notificacoes');
      lista.innerHTML = itens.length ? '' : '<p class="sub">Nenhuma notificação.</p>';
      itens.forEach((n) => {
        const el = document.createElement('div');
        el.className = 'item' + (n.loteId && usuario.perfil === 'RECEPTOR' ? '' : ' estatico');
        el.style.opacity = n.lida ? '.6' : '1';
        el.innerHTML = `<div class="foto">${n.tipo === 'NOVO_LOTE_PROXIMO' ? '🥖' : n.tipo === 'RESERVA_RECEBIDA' ? '📦' : '⏰'}</div>
          <div><div class="titulo">${escapar(n.titulo)}</div><div class="meta">${escapar(n.mensagem)}</div>
          <div class="meta">${fmtData(n.dataHora)}${n.situacao === 'FALHA' ? ' · envio falhou' : ''}</div></div>`;
        el.addEventListener('click', async () => {
          if (!n.lida) { await api('/notificacoes/' + n.id + '/lida', { method: 'POST' }).catch(() => {}); }
          if (n.loteId && usuario.perfil === 'RECEPTOR') {
            try { abrirOferta(await api('/lotes/' + n.loteId)); } catch (e) { toast(e.message); }
          }
        });
        lista.appendChild(el);
      });
    } catch (e) { lista.innerHTML = `<p class="erro">${escapar(e.message)}</p>`; }
  }

  async function atualizarBadgeNotificacoes() {
    try {
      const naoLidas = (await api('/notificacoes')).filter((n) => !n.lida).length;
      const badge = $('#badge-notif');
      badge.textContent = naoLidas;
      badge.classList.toggle('oculto', naoLidas === 0);
    } catch (e) { /* ignora */ }
  }

  // ---------- Doador: cadastrar lote (UC02) ----------
  const formLote = $('#form-lote');

  async function prepararFormLote() {
    $('#resumo-lote').classList.add('oculto');
    const sel = formLote.elements.repetir;
    sel.innerHTML = '<option value="">— novo lote —</option>';
    try {
      (await api('/lotes/meus')).forEach((l) => {
        const o = document.createElement('option');
        o.value = l.id;
        o.textContent = `#${l.id} ${l.nome} (${l.quantidade} ${l.unidadeMedida}, ${fmtData(l.dataCadastro)})`;
        sel.appendChild(o);
      });
    } catch (e) { /* lista vazia */ }
  }

  // 3a: repetir lote anterior → formulário preenchido, exceto quantidade e validade
  formLote.elements.repetir.addEventListener('change', async (ev) => {
    if (!ev.target.value) return;
    try {
      const d = await api('/lotes/' + ev.target.value + '/repetir');
      formLote.elements.nome.value = d.nome || '';
      formLote.elements.categoria.value = d.categoria || '';
      formLote.elements.unidadeMedida.value = d.unidadeMedida || '';
      formLote.elements.precoReduzido.value = d.precoReduzido ?? '';
      formLote.elements.condicoesConservacao.value = d.condicoesConservacao || '';
      formLote.elements.foto.value = d.foto || '';
      formLote.elements.quantidade.value = '';
      formLote.elements.dataValidade.value = '';
      $('#chk-endereco').checked = !!d.enderecoRetirada;
      $('#bloco-endereco').classList.toggle('oculto', !d.enderecoRetirada);
      formLote.elements.enderecoRetirada.value = d.enderecoRetirada || '';
      formLote.elements.latitude.value = d.latitude ?? '';
      formLote.elements.longitude.value = d.longitude ?? '';
      $('#urgencia').textContent = 'Informe a validade para ver a urgência';
      toast('Formulário preenchido a partir do lote anterior. Informe quantidade e validade.');
    } catch (e) { toast(e.message); }
  });

  // Passo 6: indicador de urgência ao informar a validade
  formLote.elements.dataValidade.addEventListener('change', async (ev) => {
    const el = $('#urgencia');
    if (!ev.target.value) { el.textContent = 'Informe a validade para ver a urgência'; return; }
    try {
      const u = await api('/lotes/urgencia?dataValidade=' + encodeURIComponent(paraInstant(ev.target.value)));
      el.innerHTML = `<span class="tag ${u.urgencia}">${u.urgencia}</span> ${escapar(u.descricao)}`;
    } catch (e) { el.textContent = e.message; }
  });

  // 7a: endereço de retirada diferente
  $('#chk-endereco').addEventListener('change', (ev) => $('#bloco-endereco').classList.toggle('oculto', !ev.target.checked));

  // Passo 8: confirmar o cadastro
  formLote.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const f = formLote.elements;
    const alternativo = $('#chk-endereco').checked;
    const dados = {
      nome: f.nome.value, categoria: f.categoria.value || null, quantidade: Number(f.quantidade.value || 0),
      unidadeMedida: f.unidadeMedida.value, precoReduzido: numeroOuNulo(f.precoReduzido.value), foto: f.foto.value || null,
      dataValidade: paraInstant(f.dataValidade.value), condicoesConservacao: f.condicoesConservacao.value || null,
      inicioRetirada: paraInstant(f.inicioRetirada.value), fimRetirada: paraInstant(f.fimRetirada.value),
      enderecoRetirada: alternativo ? f.enderecoRetirada.value : null,
      latitude: alternativo ? numeroOuNulo(f.latitude.value) : null, longitude: alternativo ? numeroOuNulo(f.longitude.value) : null,
    };
    $('#erro-lote').textContent = '';
    try {
      const r = await api('/lotes', { method: 'POST', body: dados });
      const n = r.notificacao;
      $('#resumo-lote').innerHTML = `<h3>Lote #${r.lote.id} publicado</h3>
        <p><strong>${escapar(r.lote.nome)}</strong> · ${r.lote.quantidade} ${escapar(r.lote.unidadeMedida)} · ${fmtPreco(r.lote)}
        · <span class="tag ${r.urgencia}">${r.urgencia}</span> ${escapar(r.urgenciaDescricao)}</p>
        <p class="meta">Retirada ${fmtData(r.lote.inicioRetirada)} – ${fmtData(r.lote.fimRetirada)} em ${escapar(r.lote.enderecoRetirada)}</p>
        <p class="meta">Notificação (UC03): ${n.motivoSemEnvio ? escapar(n.motivoSemEnvio)
          : `${n.notificados} receptor(es) notificado(s) em um raio de ${n.raioKm} km${n.raioAmpliado ? ' (raio ampliado)' : ''}${n.falhas ? ', ' + n.falhas + ' falha(s)' : ''}`}</p>`;
      $('#resumo-lote').classList.remove('oculto');
      formLote.reset();
      $('#bloco-endereco').classList.add('oculto');
      $('#urgencia').textContent = 'Informe a validade para ver a urgência';
      prepararFormLote();
      toast('Lote publicado como DISPONIVEL.');
    } catch (e) { mostrarErro(formLote, $('#erro-lote'), e); }   // 5a / 7a / 9a: mantém o que foi digitado
  });

  async function carregarMeusLotes() {
    const lista = $('#lista-meus-lotes');
    lista.innerHTML = '<p class="sub">Carregando…</p>';
    try {
      const lotes = await api('/lotes/meus');
      lista.innerHTML = lotes.length ? '' : '<p class="sub">Nenhum lote cadastrado.</p>';
      lotes.forEach((l) => lista.appendChild(cartaoLote(l, null)));
    } catch (e) { lista.innerHTML = `<p class="erro">${escapar(e.message)}</p>`; }
  }

  // ---------- Administrador: usuários (UC01) ----------
  async function carregarUsuarios() {
    const lista = $('#lista-usuarios');
    lista.innerHTML = '<p class="sub">Carregando…</p>';
    try {
      const usuarios = await api('/usuarios');
      lista.innerHTML = '';
      usuarios.forEach((u) => {
        const el = document.createElement('div');
        el.className = 'item estatico';
        el.innerHTML = `<div class="foto">${u.perfil[0]}</div>
          <div><div class="titulo">${escapar(u.nome)} <span class="tag ${u.ativo ? 'DISPONIVEL' : 'EXPIRADO'}">${u.ativo ? 'ativo' : 'inativo'}</span>
          ${u.statusAnalise ? `<span class="tag ${u.statusAnalise === 'APROVADA' ? 'BAIXA' : 'MEDIA'}">${u.statusAnalise}</span>` : ''}</div>
          <div class="meta">${u.perfil} · ${escapar(u.documento)} · ${escapar(u.email)}</div></div>
          <div class="acoes"></div>`;
        const acoes = el.querySelector('.acoes');
        if (u.perfil === 'RECEPTOR' && u.statusAnalise !== 'APROVADA') {
          const b = document.createElement('button'); b.textContent = 'Aprovar';
          b.addEventListener('click', async () => { try { await api('/usuarios/' + soDigitos(u.documento) + '/aprovacao', { method: 'POST' }); carregarUsuarios(); } catch (e) { toast(e.message); } });
          acoes.appendChild(b);
        }
        if (u.ativo && u.documento !== usuario.documento) {
          const b = document.createElement('button'); b.textContent = 'Inativar';
          b.addEventListener('click', async () => {   // 3c: confirmação antes de inativar
            try {
              const c = await api('/usuarios/' + soDigitos(u.documento) + '/inativacao');
              if (confirm(c.confirmacao)) { await api('/usuarios/' + soDigitos(u.documento), { method: 'DELETE' }); carregarUsuarios(); }
            } catch (e) { toast(e.message); }
          });
          acoes.appendChild(b);
        }
        lista.appendChild(el);
      });
    } catch (e) { lista.innerHTML = `<p class="erro">${escapar(e.message)}</p>`; }
  }

  $('#form-usuario').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const f = ev.target.elements;
    const dados = { perfil: f.perfil.value, documento: f.documento.value, nome: f.nome.value, email: f.email.value,
      telefone: f.telefone.value || null, endereco: f.endereco.value || null,
      latitude: numeroOuNulo(f.latitude.value), longitude: numeroOuNulo(f.longitude.value) };
    $('#erro-usuario').textContent = '';
    try {
      await api('/usuarios', { method: 'POST', body: dados });
      ev.target.reset();
      toast('Usuário cadastrado. E-mail de primeiro acesso registrado no log do servidor.');
      carregarUsuarios();
    } catch (e) { mostrarErro(ev.target, $('#erro-usuario'), e); }  // 6a mantém os dados; 6b mostra o existente
  });

  // ---------- início ----------
  (async function iniciar() {
    const doc = lembrado('resgatai.documento');
    if (doc) {
      try { entrar(await api('/usuarios/eu', { documento: doc })); return; } catch (e) { lembrar('resgatai.documento', null); }
    }
    mostrarTela('tela-entrar');
  })();
})();
