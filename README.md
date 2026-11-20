# Leve Teclado ⌨️

Teclado Android extremamente leve (Java puro, sem bibliotecas, uma única View desenhada no Canvas) focado em **qualidade de vida e acessibilidade**.

## Baixar
Vá em **Releases** → baixe `Leve-Teclado.apk` → instale → abra o app → passo 1 e passo 2.

## O que tem
**Área de transferência turbinada**
- Histórico de até 10.000 itens (padrão 1.000), salvo mesmo reiniciando o celular
- 🖼 Imagens copiadas e 📸 prints de tela também ficam guardados e colam com 1 toque
- Abas: Tudo · Textos · Links · Imagens · ★ Salvos
- ★ Salvar textos fixos (não somem ao limpar)
- 🔎 Busca no histórico digitando no próprio teclado
- 📎 Chip com o último copiado: 1 toque cola
- Copiar sem seleção = copia o campo inteiro
- Backup/restauração em .json

**Emojis:** tecla 😊 abre o painel com recentes e 10 categorias; ⌫ apaga o emoji inteiro.

**Sugestões estilo Gboard:** completa palavras, corrige acentos (nao → não), aprende o seu vocabulário, ⌫ logo após corrigir desfaz a correção, segurar uma sugestão esquece a palavra.

**Barra rápida:** Tudo · Copiar · Recortar · Colar · ◀ ▶ · Selecionar · ⇤ ⇥ · Desfazer/Refazer · trocar teclado · ⚙

**Gestos (inspirados no Gboard, SwiftKey e iPhone)**
- Deslizar no espaço move o cursor
- Deslizar o ⌫ para a esquerda apaga palavras; segurar acelera
- Segurar letra = acentos (á ã ç é õ) e números
- Dois espaços = ponto final; maiúscula automática
- Toque duplo no ⇧ = caps lock
- Segurar ↵ pula linha mesmo onde Enter envia
- Digitação rápida com dois dedos (rollover)
- Atalhos de texto: `@@` + espaço → seu e-mail

**Acessibilidade:** altura das teclas, tamanho das letras, tempo do toque longo, prévia ampliada, tema **Conforto** (minimalista, teclas grandes e espaçadas para dedos grandes), Alto contraste, AMOLED preto (economiza bateria), Escuro e Claro.

**Mais leve e mais seguro (atualização de estresse)**
- ⌫ apaga emoji inteiro de uma vez: 👍🏽, 👨‍👩‍👧, 🇧🇷, ❤️ e letras com acento separado
- Senhas nunca entram no histórico (campo de senha ou cópia marcada como sensível pelo app de senhas/banco)
- O teclado nunca passa de metade da tela; deitado, a fileira de números vira toque longo
- Nome das teclas diminui sozinho para caber (letra grande), botões da barra com 36 dp
- Histórico salvo em segundo plano (não trava ao copiar) e com teto de ~8 milhões de letras
  (10.000 textos normais cabem; textos gigantes antigos saem antes de pesar no celular)
- Busca no histórico e painel abrem rápido mesmo com 10.000 itens
- Dicionário aprende até 40 mil palavras (as menos usadas saem) e sugere em menos de 1 ms
- Deslizar no espaço move o cursor direto (funciona em mais apps)

## Testes de estresse
`gradle :app:testDebugUnitTest` roda 62 verificações em 10 níveis (fácil → impossível): digitação,
emojis, 50–200 mil palavras aprendidas, 10.000 cópias, textos de 5 milhões de letras, arquivos
corrompidos, 4 tamanhos de tela, 30 mil toques aleatórios com vários dedos. Placar em
`app/build/stress/teclado.tsv`. Os testes não entram no APK.

O APK é compilado automaticamente pelo GitHub Actions a cada push.
