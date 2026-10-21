@param String username
@param String confirmUrl

<div style="font-family: -apple-system, 'Segoe UI', sans-serif;
            max-width: 520px; margin: 0 auto; line-height: 1.7; color: #222">
  <h2 style="font-size: 20px">你好 ${username}，</h2>

  <p>欢迎加入 Javalinky！请点击下面的按钮确认你的账号：</p>

  <p style="margin: 28px 0">
    <a href="${confirmUrl}"
       style="background: #0d6efd; color: #fff; padding: 11px 22px;
              border-radius: 6px; text-decoration: none; display: inline-block">
      确认账号
    </a>
  </p>

  <p style="font-size: 13px; color: #666">
    按钮打不开？把这个链接复制到浏览器地址栏：<br>
    <span style="word-break: break-all">${confirmUrl}</span>
  </p>

  <hr style="border: none; border-top: 1px solid #eee; margin: 24px 0">
  <p style="font-size: 12px; color: #888">
    如果你没有注册过 Javalinky，请忽略这封邮件。这封邮件由系统自动发送，请不要回复。
  </p>
</div>
