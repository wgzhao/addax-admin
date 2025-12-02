<template>
  <form class="form-signin" action="register" method="post">
    <div class="login-wrap">
      <p>输入您的个人信息</p>
      <input type="text" id="username" placeholder="用户名" required class="form-control" />
      <input type="text" id="name" placeholder="全名" required class="form-control" />
      <input type="text" id="email" placeholder="邮箱" required class="form-control" />
      <input type="text" id="phone" placeholder="联系电话" required class="form-control" />
      <input type="password" id="password" placeholder="密码" required class="form-control" />
      <input type="password" id="password1" placeholder="确认密码" required class="form-control" />
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
