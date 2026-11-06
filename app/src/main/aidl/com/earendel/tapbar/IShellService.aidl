package com.earendel.tapbar;

interface IShellService {
    void destroy() = 16777114;
    int exec(String command) = 1;
}
