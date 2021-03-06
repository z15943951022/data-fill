package com.szz.fill.test.model;

import com.szz.fill.datafill.annonation.DataFill;
import com.szz.fill.datafill.annonation.DataFillEnable;
import com.szz.fill.test.myhandler.AuthorityHandler;
import lombok.Data;
import lombok.ToString;

/**
 * @author szz
 */
@ToString
@Data
@DataFillEnable
public class Role {

    private String roleName;

    @DataFill(value = "userId",handler = AuthorityHandler.class)
    private Authority authority;


    public Role(String roleName) {
        this.roleName = roleName;
    }
}



