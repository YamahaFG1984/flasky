/* ================================================================
   Javalin 社交博客教程 · 共享脚本：侧边栏、目录、代码高亮、主题
   ================================================================ */
(function () {
  'use strict';

  var CHAPTERS = [
    { n: 1,  t: '安装',              d: 'JDK、Maven 与第一个 Javalin 项目', part: '第一部分 · Javalin 入门' },
    { n: 2,  t: '应用的基本结构',     d: '路由、Context 与请求-响应循环' },
    { n: 3,  t: '模板',              d: 'JTE 类型安全模板、布局、静态文件与时间本地化' },
    { n: 4,  t: 'Web 表单',          d: '表单校验、CSRF、PRG 模式、会话与 Flash 消息' },
    { n: 5,  t: '数据库',            d: 'Hibernate ORM、HikariCP、JPQL 与 Flyway 迁移' },
    { n: 6,  t: '电子邮件',          d: 'Jakarta Mail、邮件模板与异步发送' },
    { n: 7,  t: '大型应用的结构',     d: '应用工厂、路由分组、配置分层与单元测试' },
    { n: 8,  t: '用户认证',          d: 'bcrypt、会话登录、注册与邮件确认', part: '第二部分 · 实例：社交博客' },
    { n: 9,  t: '用户角色',          d: '位标志权限与 RouteRole 权限校验' },
    { n: 10, t: '用户资料',          d: '资料页、资料编辑器与 Gravatar 头像' },
    { n: 11, t: '博客文章',          d: '发布、分页、Markdown 富文本与永久链接' },
    { n: 12, t: '关注',              d: '多对多、自引用关联与 JOIN 查询' },
    { n: 13, t: '用户评论',          d: '评论的存储、展示与协管' },
    { n: 14, t: '应用编程接口',       d: 'REST、令牌认证、JSON 序列化与分页' },
    { n: 15, t: '测试',              d: 'JUnit 5、JavalinTest、JaCoCo 与 Playwright', part: '第三部分 · 最后一公里' },
    { n: 16, t: '性能',              d: '慢查询日志、Server-Timing 与 JFR 剖析' },
    { n: 17, t: '部署',              d: '可执行 JAR、PaaS、Docker 与传统服务器' },
    { n: 18, t: '更多资源',          d: 'IDE、生态、社区与继续深入的路线' },

    { n: 19, t: '并发与线程安全',     d: '竞态、可见性、原子操作、限流与虚拟线程', part: '第四部分 · 进阶专题' },
    { n: 20, t: '文件上传',          d: '魔数识别、路径安全、三层大小限制与缓存头' },
    { n: 21, t: '实时：SSE 与 WebSocket', d: '服务器推送、心跳与连接回收、多实例下的失效' }
  ];

  var APPENDICES = [
    { id: 'a', t: 'Flask → Javalin 对照', d: '按功能点逐条对应，从路由到部署' },
    { id: 'b', t: '速查表',               d: '一页纸：Context、JTE、JPQL、Maven 常用写法' },
    { id: 'c', t: '报错速查',             d: '按报错信息查原因：编译不过、启动失败、行为不对' },
    { id: 'd', t: 'TypeScript 对照',      d: '同一个问题用 TS 和 Java 各写一遍' }
  ];

  var cur = parseInt(document.body.dataset.chapter || '0', 10);
  var curApp = document.body.dataset.appendix || '';
  function appFile(id) { return 'appendix-' + id + '.html'; }
  function appName(id) { return id.toUpperCase(); }

  /* ---------- 侧边栏 ---------- */
  var side = document.getElementById('sidebar');
  if (side) {
    var html = '<a class="brand" href="index.html"><span class="flame">&#9749;</span>' +
      '<span>Javalin 社交博客实战<small>Flask Web 开发 · Javalin 重写版</small></span></a>';
    CHAPTERS.forEach(function (c) {
      if (c.part) html += '<div class="part">' + c.part + '</div>';
      html += '<a class="ch' + (c.n === cur ? ' active' : '') + '" href="ch' +
        pad(c.n) + '.html"><span class="n">' + c.n + '</span><span>' + c.t + '</span></a>';
    });
    html += '<div class="part">附录</div>';
    APPENDICES.forEach(function (a) {
      html += '<a class="ch' + (a.id === curApp ? ' active' : '') + '" href="' +
        appFile(a.id) + '"><span class="n">' + appName(a.id) + '</span><span>' + a.t + '</span></a>';
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
    try { localStorage.setItem('javalin-flasky-theme', dark ? 'light' : 'dark'); } catch (e) {}
  };
  document.body.appendChild(tbtn);
  try {
    var saved = localStorage.getItem('javalin-flasky-theme');
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
      var t = '<div class="h">' + (curApp ? '目录' : '本章目录') + '</div><ol>';
      hs.forEach(function (h, i) {
        if (!h.id) h.id = 'sec-' + (i + 1);
        t += '<li><a href="#' + h.id + '">' + h.textContent + '</a></li>';
      });
      slot.className = 'toc';
      slot.innerHTML = t + '</ol>';
    }
  }

  /* ---------- 代码块：语言标签 + 复制 + 高亮 ---------- */

  /* Java */
  var JAVA_KW = ('public|private|protected|class|interface|record|enum|extends|implements|' +
    'static|final|abstract|sealed|permits|native|transient|volatile|synchronized|strictfp|' +
    'void|var|new|return|if|else|for|while|do|switch|case|default|break|continue|yield|' +
    'try|catch|finally|throw|throws|assert|instanceof|import|package|module|requires|' +
    'this|super|null|true|false|int|long|short|byte|char|float|double|boolean|' +
    'String|Integer|Long|Double|Boolean|Object').split('|');
  var RE_JAVA = new RegExp(
    '(\\/\\*[\\s\\S]*?\\*\\/|\\/\\/[^\\n]*)' +
    '|("""[\\s\\S]*?"""|"(?:\\\\[\\s\\S]|[^\\\\"\\n])*"|\'(?:\\\\[\\s\\S]|[^\\\\\'\\n])*\')' +
    '|(@[A-Za-z][\\w.]*)' +
    '|\\b(' + JAVA_KW.join('|') + ')\\b' +
    '|\\b([A-Z][A-Za-z0-9_]*)\\b' +
    '|\\b(\\d[\\d_]*(?:\\.\\d+)?[LlDdFf]?)\\b' +
    '|\\b([a-z_$][\\w$]*)(?=\\s*\\()',
    'g');

  /* JavaScript / TypeScript */
  var JS_KW = ('const|let|var|function|return|if|else|await|async|import|from|export|default|new|class|' +
    'extends|implements|for|while|of|in|do|try|catch|finally|throw|switch|case|break|continue|' +
    'type|interface|enum|as|typeof|instanceof|delete|void|yield|' +
    'null|undefined|true|false|this|super|static').split('|');
  var RE_JS = new RegExp(
    '(\\/\\*[\\s\\S]*?\\*\\/|\\/\\/[^\\n]*)' +
    '|(`(?:\\\\[\\s\\S]|[^\\\\`])*`|\'(?:\\\\[\\s\\S]|[^\\\\\'\\n])*\'|"(?:\\\\[\\s\\S]|[^\\\\"\\n])*")' +
    '|\\b(' + JS_KW.join('|') + ')\\b' +
    '|\\b([A-Z][A-Za-z0-9_]*)\\b' +
    '|\\b(\\d+(?:\\.\\d+)?)\\b' +
    '|\\b([a-zA-Z_$][\\w$]*)(?=\\()',
    'g');

  /* Python（只用于「Flask 这样写」的对照卡片） */
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
    'primary|key|foreign|references|join|inner|left|outer|on|order|by|desc|asc|limit|offset|group|count|as|' +
    'null|default|serial|bigserial|identity|integer|bigint|varchar|text|boolean|timestamp|with|without|time|zone|' +
    'alter|add|column|constraint|cascade|conflict|do|nothing|explain|analyze|truncate|restart|returning|exists|in|' +
    'distinct|having|union|all|case|when|then|end|coalesce|fetch|next|rows|only').split('|');
  var RE_SQL = new RegExp(
    '(--[^\\n]*)' +
    '|(\'(?:[^\'\\n])*\'|"[^"\\n]*")' +
    '|\\b(' + SQL_KW.join('|') + ')\\b' +
    '|\\b(\\d+)\\b',
    'gi');

  var RE_SH = /(#[^\n]*)|('(?:\\[\s\S]|[^\\'])*'|"(?:\\[\s\S]|[^\\"])*")|\b(mvn|mvnw|gradle|java|javac|jshell|jar|jlink|sdk|jenv|git|cd|mkdir|curl|http|export|docker|docker-compose|flyway|systemctl|nginx|certbot|psql|openssl|ls|cat|echo|tree|npx|npm|brew|apt|sudo|scp|ssh|kill|tail)\b/g;

  var RE_CONF = /(#[^\n]*)|('(?:\\[\s\S]|[^\\'])*'|"(?:\\[\s\S]|[^\\"])*")|^(\s*[A-Za-z_][\w.-]*)(?=\s*[:=]|\s)/gm;

  /* XML / pom.xml */
  var RE_XML = /(<!--[\s\S]*?-->)|("[^"\n]*")|(<\/?[\w:.-]+)|(\$\{[^}]*\})/g;

  /* JTE 模板：HTML + @ 指令 + ${} 输出 */
  var RE_JTE = /(<%--[\s\S]*?--%>|<!--[\s\S]*?-->)|("[^"\n]*")|(@(?:param|import|if|elseif|else|endif|for|endfor|template[\w.]*|raw|endraw)\b)|(\$unsafe\{[^}]*\}|\$\{[^}]*\}|!\{[^}]*\})|(<\/?[\w:.-]+)/g;

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
    } else if (lang === 'java') {
      pre.innerHTML = paint(code, RE_JAVA, ['tk-cm', 'tk-st', 'tk-at', 'tk-kw', 'tk-tp', 'tk-nm', 'tk-fn']);
    } else if (lang === 'xml' || lang === 'pom') {
      pre.innerHTML = paint(code, RE_XML, ['tk-cm', 'tk-st', 'tk-fn', 'tk-nm']);
    } else if (lang === 'jte') {
      pre.innerHTML = paint(code, RE_JTE, ['tk-cm', 'tk-st', 'tk-kw', 'tk-nm', 'tk-fn']);
    } else if (lang === 'python' || lang === 'py') {
      pre.innerHTML = paint(code, RE_PY, ['tk-cm', 'tk-st', 'tk-kw', 'tk-at', 'tk-nm', 'tk-fn']);
    } else if (lang === 'sql') {
      pre.innerHTML = paint(code, RE_SQL, ['tk-cm', 'tk-st', 'tk-kw', 'tk-nm']);
    } else if (lang === 'yaml' || lang === 'ini' || lang === 'dockerfile' || lang === 'nginx' ||
               lang === 'env' || lang === 'toml' || lang === 'properties' || lang === 'systemd') {
      pre.innerHTML = paint(code, RE_CONF, ['tk-cm', 'tk-st', 'tk-kw']);
    } else if (lang === 'html') {
      pre.innerHTML = paint(code, /(<!--[\s\S]*?-->)|("[^"\n]*")|(<\/?[\w-]+)/g, ['tk-cm', 'tk-st', 'tk-fn']);
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
    if (next) {
      h += '<a class="next" href="ch' + pad(next.n) + '.html"><span>下一章 &rarr;</span>第 ' + next.n + ' 章 · ' + next.t + '</a>';
    } else {
      var a0 = APPENDICES[0];
      h += '<a class="next" href="' + appFile(a0.id) + '"><span>附录 &rarr;</span>附录 ' + appName(a0.id) + ' · ' + a0.t + '</a>';
    }
    pager.className = 'pager';
    pager.innerHTML = h;
  }
  if (pager && curApp) {
    var i = APPENDICES.findIndex(function (a) { return a.id === curApp; });
    var pa = APPENDICES[i - 1], na = APPENDICES[i + 1];
    var last = CHAPTERS[CHAPTERS.length - 1];
    var h2 = '';
    h2 += pa ? '<a class="prev" href="' + appFile(pa.id) + '"><span>&larr; 上一篇</span>附录 ' + appName(pa.id) + ' · ' + pa.t + '</a>'
             : '<a class="prev" href="ch' + pad(last.n) + '.html"><span>&larr; 上一章</span>第 ' + last.n + ' 章 · ' + last.t + '</a>';
    h2 += na ? '<a class="next" href="' + appFile(na.id) + '"><span>下一篇 &rarr;</span>附录 ' + appName(na.id) + ' · ' + na.t + '</a>'
             : '<a class="next" href="index.html"><span>完结 &#127881;</span>回到课程首页</a>';
    pager.className = 'pager';
    pager.innerHTML = h2;
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
    g += '<div class="toc-part">附录</div>';
    APPENDICES.forEach(function (a) {
      g += '<a class="toc-card" href="' + appFile(a.id) + '">' +
        '<div class="n">附录 ' + appName(a.id) + '</div>' +
        '<div class="t">' + a.t + '</div>' +
        '<div class="d">' + a.d + '</div></a>';
    });
    grid.className = 'toc-grid';
    grid.innerHTML = g;
  }

  function pad(n) { return n < 10 ? '0' + n : '' + n; }
})();
