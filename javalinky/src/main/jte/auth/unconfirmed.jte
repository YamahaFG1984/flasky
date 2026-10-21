@import com.example.javalinky.web.Layout
@import com.example.javalinky.web.Routes

@param Layout layout

@template.layout.base(layout = layout, content = @`
  <div class="text-center py-5">
    <h1 class="h3">你还没有确认账号</h1>
    <p class="text-secondary">
      我们给 <strong>${layout.currentUser().getEmail()}</strong> 发过一封确认邮件。<br>
      点开里面的链接就能激活账号。
    </p>
    <p class="mt-4">
      没收到？<a href="/auth/confirm">重新发一封</a>
    </p>
    <form method="post" action="${Routes.LOGOUT}" class="mt-4">
      @template.tag.csrf(token = layout.csrfToken())
      <button class="btn btn-outline-secondary btn-sm" type="submit">换个账号登录</button>
    </form>
  </div>
`)
