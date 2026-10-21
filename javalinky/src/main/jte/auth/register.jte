@import com.example.javalinky.web.FormView
@import com.example.javalinky.web.Layout
@import com.example.javalinky.web.Routes

@param Layout layout
@param FormView form

@template.layout.base(layout = layout, content = @`
  <div class="row justify-content-center">
    <div class="col-md-5">
      <h1 class="h3 mb-4">注册</h1>

      <form method="post" action="${Routes.REGISTER}" novalidate>
        @template.tag.csrf(token = layout.csrfToken())

        @template.tag.field(form = form, name = "email", label = "邮箱",
                            type = "email", required = true,
                            autocomplete = "email")
        @template.tag.field(form = form, name = "username", label = "用户名",
                            required = true,
                            help = "以字母开头，只能包含字母、数字、点和下划线",
                            autocomplete = "username")
        @template.tag.field(form = form, name = "password", label = "密码",
                            type = "password", required = true,
                            help = "至少 8 位", autocomplete = "new-password")
        @template.tag.field(form = form, name = "password2", label = "再输入一次密码",
                            type = "password", required = true,
                            autocomplete = "new-password")

        <button class="btn btn-primary" type="submit">注册</button>
      </form>

      <p class="mt-3 text-secondary small">
        已经有账号了？<a href="${Routes.LOGIN}">去登录</a>
      </p>
    </div>
  </div>
`)
