'use strict';

const CHAVE_TOKEN = 'fiapx.token';
let token = sessionStorage.getItem(CHAVE_TOKEN);
let atualizacao = null;

const el = (id) => document.getElementById(id);

function mostrarMensagem(texto, erro = false) {
  const mensagem = el('mensagem');
  mensagem.textContent = texto;
  mensagem.className = erro ? 'erro' : 'ok';
}

async function api(caminho, opcoes = {}) {
  const headers = { ...(opcoes.headers || {}) };
  if (token) headers.Authorization = `Bearer ${token}`;
  const resposta = await fetch(caminho, { ...opcoes, headers });
  if (resposta.status === 401 && token) {
    sair();
    throw new Error('Sessão expirada. Entre novamente.');
  }
  const corpo = await resposta.json().catch(() => null);
  if (!resposta.ok) {
    throw new Error((corpo && (corpo.detail || corpo.title)) || `Erro ${resposta.status}`);
  }
  return corpo;
}

function postJson(corpo) {
  return { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(corpo) };
}

function dadosDo(form) {
  return Object.fromEntries(new FormData(form).entries());
}

async function entrar(email, password) {
  const resposta = await api('/api/v1/auth/login', postJson({ email, password }));
  token = resposta.accessToken;
  sessionStorage.setItem(CHAVE_TOKEN, token);
  mostrarPainel();
}

function sair() {
  token = null;
  sessionStorage.removeItem(CHAVE_TOKEN);
  clearInterval(atualizacao);
  el('painel').hidden = true;
  el('acesso').hidden = false;
  el('sair').hidden = true;
}

function mostrarPainel() {
  el('acesso').hidden = true;
  el('painel').hidden = false;
  el('sair').hidden = false;
  carregarVideos();
  clearInterval(atualizacao);
  atualizacao = setInterval(carregarVideos, 3000);
}

function celula(texto) {
  const td = document.createElement('td');
  td.textContent = texto;
  return td;
}

function linha(video) {
  const tr = document.createElement('tr');
  tr.append(celula(video.originalFilename));

  const status = celula(video.status);
  status.className = `status status-${video.status.toLowerCase()}`;
  if (video.errorMessage) status.title = video.errorMessage;
  tr.append(status);

  tr.append(celula(video.frameCount ?? '—'));
  tr.append(celula(new Date(video.createdAt).toLocaleString('pt-BR')));

  const acoes = document.createElement('td');
  if (video.status === 'COMPLETED') {
    const botao = document.createElement('button');
    botao.textContent = 'Baixar ZIP';
    botao.addEventListener('click', () => baixar(video.id));
    acoes.append(botao);
  } else if (video.status === 'FAILED') {
    acoes.textContent = video.errorCode;
  }
  tr.append(acoes);
  return tr;
}

function listaVazia() {
  const tr = document.createElement('tr');
  const td = celula('Nenhum vídeo ainda.');
  td.colSpan = 5;
  tr.append(td);
  return tr;
}

async function carregarVideos() {
  try {
    const pagina = await api('/api/v1/videos?size=50');
    const linhas = pagina.items.length ? pagina.items.map(linha) : [listaVazia()];
    el('lista').replaceChildren(...linhas);
  } catch (erro) {
    mostrarMensagem(erro.message, true);
  }
}

async function baixar(id) {
  try {
    const link = await api(`/api/v1/videos/${id}/download`);
    window.location.href = link.url;
  } catch (erro) {
    mostrarMensagem(erro.message, true);
  }
}

el('form-login').addEventListener('submit', async (evento) => {
  evento.preventDefault();
  const { email, password } = dadosDo(evento.target);
  try {
    await entrar(email, password);
    mostrarMensagem('');
  } catch (erro) {
    mostrarMensagem(erro.message, true);
  }
});

el('form-cadastro').addEventListener('submit', async (evento) => {
  evento.preventDefault();
  const dados = dadosDo(evento.target);
  try {
    await api('/api/v1/auth/register', postJson(dados));
    await entrar(dados.email, dados.password);
    mostrarMensagem('Conta criada.');
  } catch (erro) {
    mostrarMensagem(erro.message, true);
  }
});

el('form-upload').addEventListener('submit', async (evento) => {
  evento.preventDefault();
  const form = evento.target;
  try {
    await api('/api/v1/videos', { method: 'POST', body: new FormData(form) });
    form.reset();
    mostrarMensagem('Vídeo recebido. O status atualiza sozinho.');
    carregarVideos();
  } catch (erro) {
    mostrarMensagem(erro.message, true);
  }
});

el('sair').addEventListener('click', sair);

if (token) mostrarPainel();
