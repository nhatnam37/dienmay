import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Hình minh họa nguyên gốc bằng hình học; không ảnh/biểu trưng của hãng. */
class GenerateDemoImages {
    public static void main(String[] args)throws Exception {
        var root=Path.of(args[0]);
        for(String row:Files.readAllLines(root.resolve("products.csv")).subList(1,81)) {
            String[] c=row.split(";",-1);String category=c[1];
            var image=new BufferedImage(640,480,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(243,247,252));g.fillRect(0,0,640,480);
            var accent=Color.getHSBColor((Math.abs(c[0].hashCode())%360)/360f,0.6f,0.65f);
            g.setColor(accent);g.setStroke(new BasicStroke(8));
            if(category.equals("Điện thoại")){g.fillRoundRect(238,70,164,270,28,28);g.setColor(Color.WHITE);g.fillRoundRect(250,85,140,230,14,14);g.setColor(accent);g.fillOval(303,321,30,10);}
            else if(category.equals("Laptop")||category.equals("Tivi")){g.fillRoundRect(140,100,360,205,18,18);g.setColor(Color.WHITE);g.fillRect(155,115,330,175);g.setColor(accent);if(category.equals("Laptop"))g.fillPolygon(new int[]{140,500,550,90},new int[]{310,310,345,345},4);else {g.drawLine(320,305,320,335);g.drawLine(245,340,395,340);}}
            else if(category.equals("Tủ lạnh")){g.fillRoundRect(220,65,200,290,18,18);g.setColor(Color.WHITE);g.drawLine(225,175,415,175);g.drawLine(390,100,390,145);g.drawLine(390,205,390,255);}
            else if(category.equals("Máy giặt")){g.fillRoundRect(195,90,250,250,20,20);g.setColor(Color.WHITE);g.fillOval(243,158,155,155);g.setColor(accent);g.fillOval(263,178,115,115);g.setColor(Color.WHITE);g.fillOval(400,112,16,16);g.fillRect(220,115,90,12);}
            else if(category.equals("Điều hòa")){g.fillRoundRect(100,125,440,120,26,26);g.setColor(Color.WHITE);for(int y=180;y<=220;y+=15)g.drawLine(125,y,515,y);g.setColor(accent);for(int x=220;x<=420;x+=100)g.drawArc(x,250,35,70,90,150);}
            else if(category.equals("Loa")){g.fillRoundRect(230,75,180,280,24,24);g.setColor(Color.WHITE);g.fillOval(265,110,110,110);g.fillOval(265,230,110,110);g.setColor(accent);g.fillOval(290,135,60,60);g.fillOval(290,255,60,60);}
            else if(category.equals("Máy hút bụi")){g.fillOval(180,190,280,140);g.drawArc(300,80,180,190,0,160);g.drawLine(470,130,500,300);g.drawLine(470,300,530,300);}
            else if(category.equals("Phụ kiện")){g.fillRoundRect(230,120,180,150,20,20);g.drawLine(275,115,275,80);g.drawLine(365,115,365,80);g.drawArc(280,260,150,90,180,220);}
            else {g.fillRoundRect(170,120,300,180,20,20);g.setColor(Color.WHITE);g.drawOval(205,145,145,130);g.fillOval(395,165,18,18);g.fillOval(395,220,18,18);}
            g.setColor(new Color(20,35,60));g.setFont(new Font("SansSerif",Font.BOLD,21));g.drawString(c[0],34,405);
            g.setFont(new Font("SansSerif",Font.PLAIN,17));g.drawString("DEMO - Hinh minh hoa tu ve, khong phai anh san pham",34,445);
            g.dispose();ImageIO.write(image,"png",root.resolve("images").resolve(c[8]).toFile());
        }
    }
}
