@import com.example.javalinky.util.Gravatar
@import com.example.javalinky.web.Layout
@import com.example.javalinky.web.Routes
@import com.example.javalinky.web.page.PostListPage

@param Layout layout
@param PostListPage posts

<ul class="post-list">
@for(var post : posts.page().items())
  <li class="d-flex gap-3">
    <a href="${Routes.user(post.authorUsername())}">
      <img class="rounded" width="40" height="40" loading="lazy"
           src="${post.authorAvatar().url(40)}"
           alt="${post.authorUsername()} 的头像">
    </a>

    <div class="flex-grow-1 min-width-0">
      <div class="small text-secondary">
        @template.tag.timeago(at = post.timestamp())
      </div>
      <div>
        <a class="fw-semibold link-dark text-decoration-none"
           href="${Routes.user(post.authorUsername())}">${post.authorUsername()}</a>
      </div>

      <%-- bodyHtml 已经在服务端清洗过了，所以这里用 $unsafe --%>
      <div class="post-body">$unsafe{post.bodyHtml()}</div>

      <div class="mt-2 d-flex gap-2 small">
        @if(post.canEdit())
          <a class="badge text-bg-primary text-decoration-none"
             href="${Routes.editPost(post.id())}">编辑</a>
        @endif
        <a class="badge text-bg-secondary text-decoration-none"
           href="${Routes.post(post.id())}">永久链接</a>
        <a class="badge text-bg-light text-decoration-none border"
           href="${Routes.post(post.id())}#comments">${post.commentCount()} 条评论</a>
      </div>
    </div>
  </li>
@else
  <li class="text-secondary py-4">还没有任何文章。</li>
@endfor
</ul>
