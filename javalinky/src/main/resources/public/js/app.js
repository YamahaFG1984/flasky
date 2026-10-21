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
