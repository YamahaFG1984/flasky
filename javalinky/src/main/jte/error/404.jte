@import com.example.javalinky.web.Layout

@param Layout layout

@template.layout.base(layout = layout, content = @`
    <div class="text-center py-5">
        <h1 class="display-1 text-secondary">404</h1>
        <p class="lead">这个页面不存在。</p>
        <a class="btn btn-primary" href="/">回首页</a>
    </div>
`)
