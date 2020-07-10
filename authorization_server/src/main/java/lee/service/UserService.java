package lee.service;

import lee.mapper.UserMapper;
import lee.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 根据用户名获取User
     * @return
     */

    public User selectUserByName(String username){
        User record = new User();
        record.setUsername(username);
        User user = userMapper.selectOne(record);

        if(user==null){
            return null;
        }

        return user;

    }
}
