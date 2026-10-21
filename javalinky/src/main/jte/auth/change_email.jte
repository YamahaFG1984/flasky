@import com.example.javalinky.web.FormView
@import com.example.javalinky.web.Layout
@import com.example.javalinky.web.Routes

@param Layout layout
@param FormView form

@template.layout.base(layout = layout, content = @`
  <div class="row justify-content-center">
    <div class="col-md-5">
      <h1 class="h3 mb-4">修改邮箱</h1>
      <p class="text-secondary">
        当前邮箱：<strong>${layout.currentUser().getEmail()}</strong><br>
        新邮箱需要确认之后才会生效。
      </p>

      <form method="post" action="${Routes.CHANGE_EMAIL}" novalidate>
        @template.tag.csrf(token = layout.csrfToken())

        @template.tag.field(form = form, name = "email", label = "新邮箱",
                            type = "email", required = true,
                            autocomplete = "email")
        @template.tag.field(form = form, name = "password", label = "当前密码",
                            type = "password", required = true,
                            help = "敏感操作需要再验证一次密码",
                            autocomplete = "current-password")

        <button class="btn btn-primary" type="submit">发送确认邮件</button>
      </form>
    </div>
  </div>
`)
