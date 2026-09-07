<template>
  <form class="form-signin" action="register" method="post">
    <div class="login-wrap">
      <p>输入您的个人信息</p>
      <input id="username" type="text" placeholder="用户名" required class="form-control" />
      <input id="name" type="text" placeholder="全名" required class="form-control" />
      <input id="email" type="text" placeholder="邮箱" required class="form-control" />
      <input id="phone" type="text" placeholder="联系电话" required class="form-control" />
      <input id="password" type="password" placeholder="密码" required class="form-control" />
      <input id="password1" type="password" placeholder="确认密码" required class="form-control" />
      <button type="submit" class="btn btn-lg btn-login btn-block">
        <i class="fa fa-check"></i>
      </button>

      <div class="registration">
        已经注册。
        <a href="/login" class="">去登陆</a>
      </div>
    </div>
  </form>
</template>
