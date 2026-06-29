package com.example.yensao;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private void addCommonModel(Model model, String activeNav) {
        model.addAttribute("activeNav", activeNav);
    }

    @GetMapping({"/", "/Trangchu.html", "/trang-chu"})
    public String home(Model model) {
        addCommonModel(model, "home");
        return "Trangchu";
    }

    @GetMapping({"/san-pham", "/san_pham.html", "/San_pham.html"})
    public String sanPham(Model model) {
        addCommonModel(model, "products");
        return "San_pham";
    }

    @GetMapping({"/chi-tiet-san-pham", "/chi_tiet_san_pham.html", "/Chi_tiet_san_pham.html"})
    public String chiTietSanPham(Model model) {
        addCommonModel(model, "products");
        return "Chi_tiet_san_pham";
    }

    @GetMapping({"/tin-tuc", "/Tin_tuc.html"})
    public String tinTuc(Model model) {
        addCommonModel(model, "news");
        return "Tin_tuc";
    }

    @GetMapping({"/tin-tuc-chi-tiet", "/Tin_tuc_chi_tiet.html"})
    public String tinTucChiTiet(Model model) {
        addCommonModel(model, "news");
        return "Tin_tuc_chi_tiet";
    }

    @GetMapping({"/gioi-thieu", "/gioi_thieu.html"})
    public String gioiThieu(Model model) {
        addCommonModel(model, "about");
        return "gioi_thieu";
    }

    @GetMapping({"/lien-he", "/Lien_he.html", "/lien_he.html"})
    public String lienHe(Model model) {
        addCommonModel(model, "contact");
        return "Lien_he";
    }

    @GetMapping({"/gio-hang", "/Gio_hang.html"})
    public String gioHang(Model model) {
        addCommonModel(model, "products");
        return "Gio_hang";
    }
}
