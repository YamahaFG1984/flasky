@import com.example.javalinky.web.Layout

@param Layout layout

@template.layout.base(layout = layout, content = @`
    <div class="text-center py-5">
        <h1 class="display-1 text-secondary">500</h1>
        <p class="lead">服务器出了点问题，我们已经记录下来了。</p>
        <a class="btn btn-primary" href="/">回首页</a>
    </div>
`)
