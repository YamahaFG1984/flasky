@import com.example.javalinky.web.Layout

@param Layout layout

@template.layout.base(layout = layout, content = @`
    <div class="p-4 mb-4 bg-body-tertiary rounded-3">
        <h1 class="display-5">Hello World!</h1>
        <p class="lead text-secondary">欢迎来到 Javalinky。</p>
    </div>
`)
