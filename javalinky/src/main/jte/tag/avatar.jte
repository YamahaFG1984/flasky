@import com.example.javalinky.model.User
@import com.example.javalinky.util.Gravatar

@param User user
@param int size = 40
@param String cssClass = "rounded"

<img class="${cssClass}"
     src="${Gravatar.url(user.avatarHash(), size)}"
     srcset="${Gravatar.url(user.avatarHash(), size * 2)} 2x"
     width="${size}" height="${size}"
     alt="${user.getUsername()} 的头像"
     loading="lazy">
