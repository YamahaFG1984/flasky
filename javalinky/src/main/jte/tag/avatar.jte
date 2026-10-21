@import com.example.javalinky.model.User
@import com.example.javalinky.util.Avatar

@param User user
@param int size = 40
@param String cssClass = "rounded"

<img class="${cssClass}"
     src="${Avatar.of(user).url(size)}"
     srcset="${Avatar.of(user).url2x(size)} 2x"
     width="${size}" height="${size}"
     alt="${user.getUsername()} 的头像"
     loading="lazy">
