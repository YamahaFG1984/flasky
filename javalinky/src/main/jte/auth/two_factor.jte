@import com.example.javalinky.web.FormView
@import com.example.javalinky.web.Layout

@param Layout layout
@param FormView form

@template.layout.base(layout = layout, content = @`
  <div class="col-md-5">
    <h1 class="h3 mb-3">两步验证</h1>
    <p class="text-secondary">
      打开你的认证器 App，输入当前显示的六位数字。
      手机不在手边？也可以输入一个恢复码。
    </p>

    <form method="post" action="/auth/2fa" novalidate>
      @template.tag.csrf(token = layout.csrfToken())
      <div class="mb-3">
        <label class="form-label" for="f-code">验证码</label>
        <%-- inputmode 让手机弹数字键盘；autocomplete 让系统能自动填入 --%>
        <input class="form-control form-control-lg text-center" id="f-code" name="code"
               inputmode="numeric" autocomplete="one-time-code"
               autofocus required maxlength="14" placeholder="000000">
      </div>
      <button class="btn btn-primary" type="submit">验证</button>
      <a class="btn btn-link" href="/auth/login">取消</a>
    </form>
  </div>
`)
