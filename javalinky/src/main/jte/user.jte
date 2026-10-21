@import com.example.javalinky.web.Layout
@import com.example.javalinky.web.UserPage

@param Layout layout
@param UserPage page

@template.layout.base(layout = layout, content = @`
    <div class="pb-3 mb-4 border-bottom">
        <h1>Hello, ${page.name()}!</h1>
    </div>
    <p>你是第 ${page.visits()} 次来了。</p>
`)
