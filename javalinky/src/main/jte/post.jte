@import com.example.javalinky.web.Layout
@import com.example.javalinky.web.page.PostListPage

@param Layout layout
@param PostListPage post

@template.layout.base(layout = layout, content = @`
  <%-- 单篇文章复用列表那套渲染，保证样式一致 --%>
  @template.tag.posts(layout = layout, posts = post)

  <%-- 第 13 章在这里加评论 --%>
`)
