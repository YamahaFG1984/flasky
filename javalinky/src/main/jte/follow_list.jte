@import com.example.javalinky.util.Gravatar
@import com.example.javalinky.web.Layout
@import com.example.javalinky.web.Routes
@import com.example.javalinky.web.page.FollowListPage

@param Layout layout
@param FollowListPage page

@template.layout.base(layout = layout, content = @`
  <div class="pb-3 mb-4 border-bottom d-flex align-items-center gap-3">
    <h1 class="h4 mb-0">${page.title()}</h1>
    <a class="btn btn-sm btn-outline-secondary"
       href="${Routes.user(page.ownerUsername())}">回到主页</a>
  </div>

  <ul class="post-list">
  @for(var item : page.page().items())
    <li class="d-flex gap-3 align-items-center">
      <a href="${Routes.user(item.username())}">
        <img class="rounded" width="40" height="40" loading="lazy"
             src="${item.avatar().url(40)}"
             alt="${item.username()} 的头像">
      </a>
      <div>
        <a class="fw-semibold link-dark text-decoration-none"
           href="${Routes.user(item.username())}">${item.username()}</a>
        @if(!item.name().isEmpty())
          <span class="text-secondary small">· ${item.name()}</span>
        @endif
        <div class="small text-secondary">
          @template.tag.timeago(at = item.since())
        </div>
      </div>
    </li>
  @else
    <li class="text-secondary py-4">这里还空着。</li>
  @endfor
  </ul>

  @template.tag.pagination(page = page.page(), baseUrl = page.baseUrl())
`)
