INSERT INTO sys_role (role_code, role_name)
VALUES ('APP_USER', '问答前台用户')
ON CONFLICT (role_code) DO NOTHING;
