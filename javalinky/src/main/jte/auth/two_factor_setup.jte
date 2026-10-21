@import com.example.javalinky.web.Layout
@import java.util.List

@param Layout layout
@param boolean enabled
@param String secret = null
@param String uri = null
@param long remaining = 0
@param List<String> codes = List.of()

@template.layout.base(layout = layout, content = @`
  <div class="col-lg-7">
    <h1 class="h3 mb-4">两步验证</h1>

    @if(!codes.isEmpty())
      <div class="alert alert-warning">
        <h2 class="h5">请把这些恢复码存好</h2>
        <p class="mb-2">
          每个只能用一次。<strong>这是唯一一次显示它们</strong> ——
          我们存的是哈希，离开这个页面就再也拿不回来了。
        </p>
        <pre class="mb-0">@for(var c : codes)${c}
@endfor</pre>
      </div>
    @endif

    @if(enabled)
      <p>
        <span class="badge text-bg-success">已开启</span>
        剩余可用恢复码：<strong>${remaining}</strong> 个
      </p>

      <form method="post" action="/auth/2fa/disable" class="mt-4 col-md-7">
        @template.tag.csrf(token = layout.csrfToken())
        <p class="text-secondary">关闭两步验证需要再输一次密码。</p>
        <div class="mb-3">
          <label class="form-label" for="f-password">密码</label>
          <input class="form-control" type="password" id="f-password" name="password" required>
        </div>
        <button class="btn btn-outline-danger" type="submit">关闭两步验证</button>
      </form>
    @else
      <ol class="mb-4">
        <li>装一个认证器 App（Google Authenticator、1Password、Authy 都行）</li>
        <li>扫下面的二维码，或者手动输入密钥</li>
        <li>把 App 显示的六位数字填进来，确认配置成功</li>
      </ol>

      <div class="mb-3">
        <div class="form-text">手动输入用的密钥</div>
        <code class="user-select-all fs-5">${secret}</code>
      </div>

      <div class="mb-4">
        <div class="form-text">otpauth 链接（把它生成二维码给用户扫）</div>
        <code class="user-select-all small">${uri}</code>
      </div>

      <form method="post" action="/auth/2fa/enable" novalidate class="col-md-6">
        @template.tag.csrf(token = layout.csrfToken())
        <div class="mb-3">
          <label class="form-label" for="f-code">App 上显示的验证码</label>
          <input class="form-control" id="f-code" name="code"
                 inputmode="numeric" autocomplete="one-time-code"
                 required maxlength="6" placeholder="000000">
        </div>
        <button class="btn btn-primary" type="submit">开启两步验证</button>
      </form>
    @endif
  </div>
`)
