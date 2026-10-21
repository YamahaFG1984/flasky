// 把所有 <time datetime="..."> 换成本地化的显示
(function () {
  const rtf = new Intl.RelativeTimeFormat(navigator.language, { numeric: 'auto' });
  const abs = new Intl.DateTimeFormat(navigator.language, {
    dateStyle: 'long', timeStyle: 'short'
  });

  const UNITS = [
    ['year',   31536000],
    ['month',  2592000],
    ['week',   604800],
    ['day',    86400],
    ['hour',   3600],
    ['minute', 60],
    ['second', 1]
  ];

  function relative(date) {
    const diffSec = (date - Date.now()) / 1000;          // 过去是负数
    for (const [unit, secs] of UNITS) {
      if (Math.abs(diffSec) >= secs || unit === 'second') {
        return rtf.format(Math.round(diffSec / secs), unit);
      }
    }
  }

  function upgrade(el) {
    const date = new Date(el.getAttribute('datetime'));
    if (isNaN(date)) return;
    el.title = abs.format(date);                          // 鼠标悬停看精确时间
    el.textContent = el.dataset.style === 'absolute'
      ? abs.format(date)
      : relative(date);
  }

  document.querySelectorAll('time[datetime]').forEach(upgrade);

  // 每分钟刷新一次相对时间（“3 分钟前” → “4 分钟前”）
  setInterval(function () {
    document.querySelectorAll('time[datetime][data-style="relative"]').forEach(upgrade);
  }, 60000);
})();

/* ---------- 第 21 章：实时通知（SSE） ---------- */
(function () {
  // 只有登录用户才有这个流。用 body 上的标记判断，省得未登录时白连一次。
  if (!document.body.dataset.authenticated) return;
  if (typeof EventSource === 'undefined') return;

  var source = new EventSource('/live');

  source.addEventListener('comment', function (e) {
    var d = JSON.parse(e.data);
    toast('<a class="link-light" href="' + d.postUrl + '">' +
          escapeHtml(d.author) + ' 评论了你的文章：' + escapeHtml(d.excerpt) + '</a>');
  });

  // EventSource 自带重连（默认 3 秒），所以这里什么都不用做。
  // 真正需要处理的是「服务器明确说别再连了」—— 那种情况下要 close()。
  source.addEventListener('error', function () {
    if (source.readyState === EventSource.CLOSED) {
      console.info('实时通知已断开，不再重连');
    }
  });

  function toast(html) {
    var box = document.getElementById('toast-box');
    if (!box) {
      box = document.createElement('div');
      box.id = 'toast-box';
      box.className = 'toast-container position-fixed bottom-0 end-0 p-3';
      document.body.appendChild(box);
    }
    var el = document.createElement('div');
    el.className = 'toast align-items-center text-bg-dark border-0 show';
    el.innerHTML = '<div class="d-flex"><div class="toast-body">' + html +
      '</div><button type="button" class="btn-close btn-close-white me-2 m-auto"' +
      ' data-bs-dismiss="toast"></button></div>';
    box.appendChild(el);
    setTimeout(function () { el.remove(); }, 12000);
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
})();
