/* ================================================================
   Hono 社交博客教程 · 共享脚本：侧边栏、目录、代码高亮、主题
   ================================================================ */
(function () {
  'use strict';

  var CHAPTERS = [
    { n: 1,  t: '安装',               d: 'Node.js、包管理器与第一个 Hono 项目', part: '第一部分 · Hono 入门' },
    { n: 2,  t: '应用的基本结构',      d: '路由、Context 与请求-响应循环' },
    { n: 3,  t: '模板',               d: 'JSX 服务端渲染、布局、静态文件与本地化时间' },
    { n: 4,  t: 'Web 表单',           d: 'Zod 校验、PRG 模式、会话与 Flash 消息' },
    { n: 5,  t: '数据库',             d: 'Drizzle ORM、PGlite 与数据库迁移' },
    { n: 6,  t: '电子邮件',           d: 'Nodemailer、邮件模板与异步发送' },
    { n: 7,  t: '大型应用的结构',      d: '应用工厂、路由分组、配置与单元测试' },
    { n: 8,  t: '用户认证',           d: '密码哈希、会话登录、注册与邮件确认', part: '第二部分 · 实例：社交博客' },
    { n: 9,  t: '用户角色',           d: '用位运算表示权限，用中间件检查权限' },
    { n: 10, t: '用户资料',           d: '资料页、资料编辑器与 Gravatar 头像' },
    { n: 11, t: '博客文章',           d: '发布、分页、Markdown 富文本与永久链接' },
    { n: 12, t: '关注',               d: '多对多、自引用关系与 JOIN 查询' },
    { n: 13, t: '用户评论',           d: '评论的存储、展示与协管' },
    { n: 14, t: '应用编程接口',        d: 'REST、令牌认证、JSON 序列化与分页' },
    { n: 15, t: '测试',               d: 'Vitest、测试客户端、覆盖率与 Playwright', part: '第三部分 · 最后一公里' },
    { n: 16, t: '性能',               d: '慢查询日志、Server-Timing 与 CPU 剖析' },
    { n: 17, t: '部署',               d: '部署脚本、错误日志、PaaS、Docker 与传统服务器' },
    { n: 18, t: '更多资源',           d: 'IDE、生态、社区与继续深入的路线' }
  ];

  var cur = parseInt(document.body.dataset.chapter || '0', 10);

  /* ---------- 侧边栏 ---------- */
  var side = document.getElementById('sidebar');
  if (side) {
    var html = '<a class="brand" href="index.html"><span class="flame">&#128293;</span>' +
      '<span>Hono 社交博客实战<small>Flask Web 开发 · Hono 重写版</small></span></a>';
    CHAPTERS.forEach(function (c) {
      if (c.part) html += '<div class="part">' + c.part + '</div>';
      html += '<a class="ch' + (c.n === cur ? ' active' : '') + '" href="ch' +
        pad(c.n) + '.html"><span class="n">' + c.n + '</span><span>' + c.t + '</span></a>';
    });
    side.innerHTML = html;
    var active = side.querySelector('a.ch.active');
    if (active) setTimeout(function () { active.scrollIntoView({ block: 'center' }); }, 0);
  }

  /* ---------- 移动端菜单 ---------- */
  var btn = document.createElement('button');
  btn.id = 'menu-btn';
  btn.type = 'button';
  btn.setAttribute('aria-label', '目录');
  btn.innerHTML = '&#9776;';
  btn.onclick = function () { document.body.classList.toggle('nav-open'); };
  document.body.appendChild(btn);
  document.addEventListener('click', function (e) {
    if (document.body.classList.contains('nav-open') &&
        side && !side.contains(e.target) && e.target !== btn) {
      document.body.classList.remove('nav-open');
    }
  });

  /* ---------- 主题切换 ---------- */
  var tbtn = document.createElement('button');
  tbtn.id = 'theme-btn';
  tbtn.type = 'button';
  tbtn.setAttribute('aria-label', '切换深浅色');
  tbtn.innerHTML = '&#9789;';
  tbtn.onclick = function () {
    var root = document.documentElement;
    var now = root.getAttribute('data-theme');
    var dark = now ? now === 'dark'
      : window.matchMedia('(prefers-color-scheme: dark)').matches;
    root.setAttribute('data-theme', dark ? 'light' : 'dark');
    try { localStorage.setItem('hono-flasky-theme', dark ? 'light' : 'dark'); } catch (e) {}
  };
  document.body.appendChild(tbtn);
  try {
    var saved = localStorage.getItem('hono-flasky-theme');
    if (saved) document.documentElement.setAttribute('data-theme', saved);
  } catch (e) {}

  /* ---------- 箭头 marker（全局一次） ---------- */
  var defs = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  defs.setAttribute('width', '0'); defs.setAttribute('height', '0');
  defs.setAttribute('style', 'position:absolute');
  // SVG marker 的内容不会从引用它的元素继承 color，所以直接用 CSS 变量填色
  function marker(id, color) {
    return '<marker id="' + id + '" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">' +
      '<path d="M0,0 L10,5 L0,10 z" fill="var(' + color + ')"/></marker>';
  }
  defs.innerHTML = '<defs>' +
    marker('ar', '--fg-faint') + marker('ar-a', '--accent') + marker('ar-b', '--blue') +
    marker('ar-g', '--green') + marker('ar-r', '--red') + marker('ar-p', '--purple') +
    '</defs>';
  document.body.appendChild(defs);

  /* ---------- 章节内目录 ---------- */
  var main = document.querySelector('main');
  var slot = document.getElementById('chapter-toc');
  if (slot && main) {
    var hs = main.querySelectorAll('h2');
    if (hs.length > 2) {
      var t = '<div class="h">本章目录</div><ol>';
      hs.forEach(function (h, i) {
        if (!h.id) h.id = 'sec-' + (i + 1);
        t += '<li><a href="#' + h.id + '">' + h.textContent + '</a></li>';
      });
      slot.className = 'toc';
      slot.innerHTML = t + '</ol>';
    }
  }

  /* ---------- 代码块：语言标签 + 复制 + 高亮 ---------- */
  var KW = ('const|let|var|function|return|if|else|await|async|import|from|export|default|new|class|' +
    'extends|implements|for|while|of|in|do|try|catch|finally|throw|switch|case|break|continue|' +
    'type|interface|enum|as|satisfies|typeof|instanceof|delete|void|yield|public|private|readonly|' +
    'null|undefined|true|false|this|super|static|declare|namespace|keyof').split('|');

  var RE_JS = new RegExp(
    '(\\/\\*[\\s\\S]*?\\*\\/|\\/\\/[^\\n]*)' +
    '|(`(?:\\\\[\\s\\S]|[^\\\\`])*`|\'(?:\\\\[\\s\\S]|[^\\\\\'\\n])*\'|"(?:\\\\[\\s\\S]|[^\\\\"\\n])*")' +
    '|\\b(' + KW.join('|') + ')\\b' +
    '|\\b([A-Z][A-Za-z0-9_]*)\\b' +
    '|\\b(\\d+(?:\\.\\d+)?)\\b' +
    '|\\b([a-zA-Z_$][\\w$]*)(?=\\()',
    'g');

  var PY_KW = ('def|return|class|import|from|as|if|elif|else|for|while|in|not|and|or|is|with|try|except|' +
    'finally|raise|pass|lambda|yield|None|True|False|self|global|assert|del|break|continue').split('|');
  var RE_PY = new RegExp(
    '(#[^\\n]*)' +
    '|("""[\\s\\S]*?"""|\'(?:\\\\[\\s\\S]|[^\\\\\'\\n])*\'|"(?:\\\\[\\s\\S]|[^\\\\"\\n])*")' +
    '|\\b(' + PY_KW.join('|') + ')\\b' +
    '|(@[\\w.]+)' +
    '|\\b(\\d+(?:\\.\\d+)?)\\b' +
    '|\\b([a-zA-Z_][\\w]*)(?=\\()',
    'g');

  var SQL_KW = ('select|from|where|and|or|not|insert|into|values|update|set|delete|create|table|index|unique|' +
    'primary|key|foreign|references|join|inner|left|on|order|by|desc|asc|limit|offset|group|count|as|' +
    'null|default|serial|integer|varchar|text|boolean|timestamp|with|time|zone|alter|add|constraint|' +
    'cascade|conflict|do|nothing|explain|analyze|truncate|restart|identity|returning|exists|in').split('|');
  var RE_SQL = new RegExp(
    '(--[^\\n]*)' +
    '|(\'(?:[^\'\\n])*\'|"[^"\\n]*")' +
    '|\\b(' + SQL_KW.join('|') + ')\\b' +
    '|\\b(\\d+)\\b',
    'gi');

  var RE_SH = /(#[^\n]*)|('(?:\\[\s\S]|[^\\'])*'|"(?:\\[\s\S]|[^\\"])*")|\b(npm|npx|pnpm|yarn|bun|deno|node|git|cd|mkdir|curl|export|docker|heroku|sudo|apt|brew|nvm|fnm|tsx|vitest|systemctl|pip|python|flask|ls|cat|echo|openssl)\b/g;

  var RE_CONF = /(#[^\n]*)|('(?:\\[\s\S]|[^\\'])*'|"(?:\\[\s\S]|[^\\"])*")|^(\s*[A-Za-z_][\w.-]*)(?=\s*[:=]|\s)/gm;

  function esc(s) {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  function paint(src, re, classes) {
    var out = '', last = 0, m;
    re.lastIndex = 0;
    while ((m = re.exec(src)) !== null) {
      out += esc(src.slice(last, m.index));
      for (var g = 1; g < m.length; g++) {
        if (m[g] !== undefined) { out += '<span class="' + classes[g - 1] + '">' + esc(m[g]) + '</span>'; break; }
      }
      last = m.index + m[0].length;
      if (m[0].length === 0) re.lastIndex++;
    }
    return out + esc(src.slice(last));
  }

  document.querySelectorAll('.code').forEach(function (box) {
    var pre = box.querySelector('pre');
    if (!pre) return;
    var lang = box.dataset.lang || '';
    var file = box.dataset.file || '';
    var bar = document.createElement('div');
    bar.className = 'bar';
    bar.innerHTML = '<span class="tag">' + esc(file || lang || 'code') + '</span>';
    var cp = document.createElement('button');
    cp.className = 'copy'; cp.type = 'button'; cp.textContent = '复制';
    cp.onclick = function () {
      var txt = pre.textContent;
      if (navigator.clipboard) navigator.clipboard.writeText(txt);
      cp.textContent = '已复制'; setTimeout(function () { cp.textContent = '复制'; }, 1400);
    };
    bar.appendChild(cp);
    box.insertBefore(bar, pre);

    var code = pre.textContent.replace(/^\n/, '').replace(/\s+$/, '');
    if (lang === 'bash' || lang === 'sh' || lang === 'shell' || lang === 'text' || lang === 'http') {
      pre.innerHTML = paint(code, RE_SH, ['tk-cm', 'tk-st', 'tk-kw']);
    } else if (lang === 'python' || lang === 'py') {
      pre.innerHTML = paint(code, RE_PY, ['tk-cm', 'tk-st', 'tk-kw', 'tk-tp', 'tk-nm', 'tk-fn']);
    } else if (lang === 'sql') {
      pre.innerHTML = paint(code, RE_SQL, ['tk-cm', 'tk-st', 'tk-kw', 'tk-nm']);
    } else if (lang === 'yaml' || lang === 'ini' || lang === 'dockerfile' || lang === 'nginx' || lang === 'env' || lang === 'toml') {
      pre.innerHTML = paint(code, RE_CONF, ['tk-cm', 'tk-st', 'tk-kw']);
    } else if (lang === 'html' || lang === 'jinja') {
      pre.innerHTML = paint(code, /(\{#[\s\S]*?#\}|<!--[\s\S]*?-->)|("[^"\n]*")|(\{%[\s\S]*?%\}|\{\{[\s\S]*?\}\})|(<\/?[\w-]+)/g,
        ['tk-cm', 'tk-st', 'tk-kw', 'tk-fn']);
    } else {
      pre.innerHTML = paint(code, RE_JS, ['tk-cm', 'tk-st', 'tk-kw', 'tk-tp', 'tk-nm', 'tk-fn']);
    }
  });

  /* ---------- 上一章 / 下一章 ---------- */
  var pager = document.getElementById('pager');
  if (pager && cur) {
    var prev = CHAPTERS.find(function (c) { return c.n === cur - 1; });
    var next = CHAPTERS.find(function (c) { return c.n === cur + 1; });
    var h = '';
    h += prev ? '<a class="prev" href="ch' + pad(prev.n) + '.html"><span>&larr; 上一章</span>第 ' + prev.n + ' 章 · ' + prev.t + '</a>'
              : '<a class="prev" href="index.html"><span>&larr; 返回</span>课程首页</a>';
    h += next ? '<a class="next" href="ch' + pad(next.n) + '.html"><span>下一章 &rarr;</span>第 ' + next.n + ' 章 · ' + next.t + '</a>'
              : '<a class="next" href="index.html"><span>完结 &#127881;</span>回到课程首页</a>';
    pager.className = 'pager';
    pager.innerHTML = h;
  }

  /* ---------- 首页目录 ---------- */
  var grid = document.getElementById('toc-grid');
  if (grid) {
    var g = '';
    CHAPTERS.forEach(function (c) {
      if (c.part) g += '<div class="toc-part">' + c.part + '</div>';
      g += '<a class="toc-card" href="ch' + pad(c.n) + '.html">' +
        '<div class="n">第 ' + c.n + ' 章</div>' +
        '<div class="t">' + c.t + '</div>' +
        '<div class="d">' + c.d + '</div></a>';
    });
    grid.className = 'toc-grid';
    grid.innerHTML = g;
  }

  function pad(n) { return n < 10 ? '0' + n : '' + n; }
})();
