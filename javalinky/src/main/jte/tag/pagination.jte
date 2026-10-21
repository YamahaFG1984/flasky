@import com.example.javalinky.db.Page

<%-- baseUrl 形如 "/" 或 "/user/john"；fragment 形如 "#comments" --%>
@param Page<?> page
@param String baseUrl
@param String fragment = ""

@if(page.totalPages() > 1)
  <nav aria-label="分页">
    <ul class="pagination">

      <li class="page-item ${page.hasPrevious() ? "" : "disabled"}">
        <a class="page-link"
           href="${page.hasPrevious()
                   ? baseUrl + "?page=" + page.previousPage() + fragment
                   : "#"}"
           aria-label="上一页">&laquo;</a>
      </li>

      @for(Integer n : page.pageNumbers())
        @if(n == 0)
          <li class="page-item disabled"><span class="page-link">&hellip;</span></li>
        @elseif(n == page.page())
          <li class="page-item active" aria-current="page">
            <span class="page-link">${n}</span>
          </li>
        @else
          <li class="page-item">
            <a class="page-link" href="${baseUrl + "?page=" + n + fragment}">${n}</a>
          </li>
        @endif
      @endfor

      <li class="page-item ${page.hasNext() ? "" : "disabled"}">
        <a class="page-link"
           href="${page.hasNext()
                   ? baseUrl + "?page=" + page.nextPage() + fragment
                   : "#"}"
           aria-label="下一页">&raquo;</a>
      </li>
    </ul>
  </nav>
@endif
