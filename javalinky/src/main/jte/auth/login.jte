@import com.example.javalinky.web.FormView
@import com.example.javalinky.web.Layout
@import com.example.javalinky.web.Routes

@param Layout layout
@param FormView form

@template.layout.base(layout = layout, content = @`
  <div class="row justify-content-center">
    <div class="col-md-5">
      <h1 class="h3 mb-4">登录</h1>

      <form method="post" action="${Routes.LOGIN}" novalidate>
        @template.tag.csrf(token = layout.csrfToken())

        @template.tag.field(form = form, name = "email", label = "邮箱",
                            type = "email", required = true,
                            autocomplete = "username")
        @template.tag.field(form = form, name = "password", label = "密码",
                            type = "password", required = true,
                            autocomplete = "current-password")
        @template.tag.checkbox(form = form, name = "remember", label = "记住我")

        <button class="btn btn-primary" type="submit">登录</button>
      </form>

      <p class="mt-3 text-secondary small">
        还没有账号？<a href="${Routes.REGISTER}">注册一个</a>
      </p>
    </div>
  </div>
`)
