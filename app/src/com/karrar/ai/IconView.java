package com.karrar.ai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Vector-style icons drawn on a 24x24 grid, so no image resources are needed. */
public class IconView extends View {
    public static final int MENU = 0, NEWCHAT = 1, SEND = 2, STOP = 3, COPY = 4, REFRESH = 5,
            LIKE = 6, DISLIKE = 7, GLOBE = 8, ATOM = 9, SETTINGS = 10, TRASH = 11, CHEV_DOWN = 12,
            CHEV_RIGHT = 13, CLOSE = 14, PLUS = 15, USER = 16, CHECK = 17, SHARE = 18, LOGO = 19,
            BACK = 20, KEY = 21, LINK = 22, PALETTE = 23, LANG = 24, INFO = 25, DATABASE = 26,
            EDIT = 27, CHEV_LEFT = 28, CHAT = 29, SPARK = 30, FILE = 31,
            NEWCHAT2 = 32, SOUND_OFF = 33, SOUND_ON = 34, VOICE = 35, SEARCH = 36, PIN = 37, MULTI = 38,
            CAMERA = 39, IMAGE = 40, CLIP = 41, MORE = 42, MOON = 43, TEXTSIZE = 44, WAND = 45, MIC = 46,
            HELP = 47, LOGOUT = 48, PHONE = 49, DOC = 50, MAIL = 51, AVATAR = 52;

    private int type;
    private int color = 0xFFFFFFFF;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    public IconView(Context c, int type, int color) {
        super(c);
        this.type = type;
        this.color = color;
    }

    public void setType(int t) { type = t; invalidate(); }
    public void setColor(int c) { color = c; invalidate(); }

    private void line(Canvas c, float x1, float y1, float x2, float y2) { c.drawLine(x1, y1, x2, y2, p); }

    private void poly(Canvas c, float... v) {
        path.reset();
        path.moveTo(v[0], v[1]);
        for (int i = 2; i < v.length; i += 2) path.lineTo(v[i], v[i + 1]);
        c.drawPath(path, p);
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / 24f;
        c.save();
        c.translate((getWidth() - 24 * s) / 2f, (getHeight() - 24 * s) / 2f);
        c.scale(s, s);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.9f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        switch (type) {
            case MENU:
                line(c, 4, 8, 20, 8);
                line(c, 4, 16, 14, 16);
                break;
            case NEWCHAT:
                path.reset();
                path.moveTo(11, 4.5f); path.lineTo(7, 4.5f);
                r.set(4, 4.5f, 17, 20); path.addRoundRect(r, 2.5f, 2.5f, Path.Direction.CW);
                path.reset();
                path.moveTo(11, 4.5f);
                path.lineTo(7, 4.5f);
                path.quadTo(4.5f, 4.5f, 4.5f, 7);
                path.lineTo(4.5f, 17);
                path.quadTo(4.5f, 19.5f, 7, 19.5f);
                path.lineTo(17, 19.5f);
                path.quadTo(19.5f, 19.5f, 19.5f, 17);
                path.lineTo(19.5f, 13);
                c.drawPath(path, p);
                poly(c, 9.5f, 14.5f, 10, 11.5f, 18.2f, 3.3f, 20.7f, 5.8f, 12.5f, 14f, 9.5f, 14.5f);
                break;
            case SEND:
                line(c, 12, 19, 12, 6);
                poly(c, 6.2f, 11.5f, 12, 5.7f, 17.8f, 11.5f);
                break;
            case STOP:
                p.setStyle(Paint.Style.FILL);
                r.set(7, 7, 17, 17);
                c.drawRoundRect(r, 2, 2, p);
                break;
            case COPY:
                r.set(9, 9, 19.5f, 19.5f);
                c.drawRoundRect(r, 2.2f, 2.2f, p);
                path.reset();
                path.moveTo(15, 9); path.lineTo(15, 6.5f);
                path.quadTo(15, 4.5f, 13, 4.5f);
                path.lineTo(6.5f, 4.5f);
                path.quadTo(4.5f, 4.5f, 4.5f, 6.5f);
                path.lineTo(4.5f, 13);
                path.quadTo(4.5f, 15, 6.5f, 15);
                path.lineTo(9, 15);
                c.drawPath(path, p);
                break;
            case REFRESH:
                r.set(5, 5, 19, 19);
                c.drawArc(r, -30, 290, false, p);
                poly(c, 18.2f, 3.8f, 18.2f, 8.2f, 13.8f, 8.2f);
                break;
            case LIKE:
            case DISLIKE:
                c.save();
                if (type == DISLIKE) c.rotate(180, 12, 12);
                r.set(3.5f, 10.5f, 7.5f, 19.5f);
                c.drawRoundRect(r, 1.2f, 1.2f, p);
                path.reset();
                path.moveTo(7.5f, 11);
                path.lineTo(11.5f, 4.2f);
                path.quadTo(14.2f, 4.2f, 13.8f, 7.2f);
                path.lineTo(13.4f, 9.5f);
                path.lineTo(18.2f, 9.5f);
                path.quadTo(20.4f, 9.6f, 20f, 11.8f);
                path.lineTo(19f, 17.4f);
                path.quadTo(18.6f, 19.5f, 16.6f, 19.5f);
                path.lineTo(7.5f, 19.5f);
                c.drawPath(path, p);
                c.restore();
                break;
            case GLOBE:
                c.drawCircle(12, 12, 8.6f, p);
                r.set(8, 3.4f, 16, 20.6f);
                c.drawOval(r, p);
                line(c, 3.4f, 12, 20.6f, 12);
                break;
            case ATOM:
                for (int i = 0; i < 3; i++) {
                    c.save();
                    c.rotate(i * 60, 12, 12);
                    r.set(2.8f, 8.2f, 21.2f, 15.8f);
                    c.drawOval(r, p);
                    c.restore();
                }
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 12, 1.6f, p);
                break;
            case SETTINGS:
                c.drawCircle(12, 12, 3f, p);
                c.drawCircle(12, 12, 7.2f, p);
                for (int i = 0; i < 8; i++) {
                    c.save();
                    c.rotate(i * 45, 12, 12);
                    line(c, 12, 2.6f, 12, 4.8f);
                    c.restore();
                }
                break;
            case TRASH:
                line(c, 4, 7, 20, 7);
                poly(c, 9, 7, 9, 4.2f, 15, 4.2f, 15, 7);
                poly(c, 6, 7, 7, 19.8f, 17, 19.8f, 18, 7);
                line(c, 10.2f, 11, 10.2f, 16);
                line(c, 13.8f, 11, 13.8f, 16);
                break;
            case CHEV_DOWN:
                poly(c, 6, 9.2f, 12, 15f, 18, 9.2f);
                break;
            case CHEV_RIGHT:
                poly(c, 9.2f, 6, 15f, 12, 9.2f, 18);
                break;
            case CHEV_LEFT:
                poly(c, 14.8f, 6, 9f, 12, 14.8f, 18);
                break;
            case BACK:
                line(c, 20, 12, 5, 12);
                poly(c, 11, 6, 5, 12, 11, 18);
                break;
            case CLOSE:
                line(c, 6, 6, 18, 18);
                line(c, 18, 6, 6, 18);
                break;
            case PLUS:
                line(c, 12, 5, 12, 19);
                line(c, 5, 12, 19, 12);
                break;
            case USER:
                c.drawCircle(12, 8, 4f, p);
                path.reset();
                path.moveTo(4.5f, 20.5f);
                path.quadTo(4.5f, 14, 12, 14);
                path.quadTo(19.5f, 14, 19.5f, 20.5f);
                c.drawPath(path, p);
                break;
            case CHECK:
                poly(c, 5, 12.5f, 10, 17.2f, 19, 7.2f);
                break;
            case SHARE:
                line(c, 12, 14.5f, 12, 3.5f);
                poly(c, 7.8f, 7.5f, 12, 3.5f, 16.2f, 7.5f);
                poly(c, 6.5f, 11, 5.5f, 11, 5.5f, 20, 18.5f, 20, 18.5f, 11, 17.5f, 11);
                break;
            case KEY:
                c.drawCircle(8, 15.5f, 4f, p);
                line(c, 11, 12.5f, 20, 3.5f);
                line(c, 16.5f, 7f, 19f, 9.5f);
                break;
            case LINK:
                r.set(3, 8.5f, 12, 15.5f);
                c.drawRoundRect(r, 3.5f, 3.5f, p);
                r.set(12, 8.5f, 21, 15.5f);
                c.drawRoundRect(r, 3.5f, 3.5f, p);
                line(c, 9, 12, 15, 12);
                break;
            case PALETTE:
                c.drawCircle(12, 12, 8.6f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(8.4f, 10, 1.2f, p);
                c.drawCircle(12, 7.6f, 1.2f, p);
                c.drawCircle(15.8f, 10.4f, 1.2f, p);
                c.drawCircle(15.2f, 15f, 1.2f, p);
                break;
            case LANG:
                c.drawCircle(12, 12, 8.6f, p);
                line(c, 3.4f, 12, 20.6f, 12);
                path.reset();
                path.moveTo(12, 3.4f);
                path.quadTo(7, 12, 12, 20.6f);
                path.moveTo(12, 3.4f);
                path.quadTo(17, 12, 12, 20.6f);
                c.drawPath(path, p);
                break;
            case INFO:
                c.drawCircle(12, 12, 8.6f, p);
                line(c, 12, 11, 12, 16.5f);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 7.9f, 1.15f, p);
                break;
            case DATABASE:
                r.set(5, 3.8f, 19, 9.2f);
                c.drawOval(r, p);
                line(c, 5, 6.5f, 5, 17.5f);
                line(c, 19, 6.5f, 19, 17.5f);
                path.reset();
                path.moveTo(5, 12);
                path.quadTo(12, 17.5f, 19, 12);
                path.moveTo(5, 17.5f);
                path.quadTo(12, 23f, 19, 17.5f);
                c.drawPath(path, p);
                break;
            case EDIT:
                poly(c, 4.5f, 19.5f, 5.3f, 15.5f, 15.8f, 5f, 19f, 8.2f, 8.5f, 18.7f, 4.5f, 19.5f);
                line(c, 13.6f, 7.2f, 16.8f, 10.4f);
                break;
            case CHAT:
                path.reset();
                path.moveTo(7, 4.5f);
                path.lineTo(17, 4.5f);
                path.quadTo(19.5f, 4.5f, 19.5f, 7);
                path.lineTo(19.5f, 13);
                path.quadTo(19.5f, 15.5f, 17, 15.5f);
                path.lineTo(11, 15.5f);
                path.lineTo(7, 19.5f);
                path.lineTo(7, 15.5f);
                path.quadTo(4.5f, 15.5f, 4.5f, 13);
                path.lineTo(4.5f, 7);
                path.quadTo(4.5f, 4.5f, 7, 4.5f);
                c.drawPath(path, p);
                break;
            case SPARK:
                p.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(12, 2.5f);
                path.quadTo(12.8f, 10.6f, 21.5f, 12);
                path.quadTo(12.8f, 13.4f, 12, 21.5f);
                path.quadTo(11.2f, 13.4f, 2.5f, 12);
                path.quadTo(11.2f, 10.6f, 12, 2.5f);
                c.drawPath(path, p);
                break;
            case FILE:
                poly(c, 6, 3.5f, 14, 3.5f, 19, 8.5f, 19, 20.5f, 6, 20.5f, 6, 3.5f);
                poly(c, 14, 3.5f, 14, 8.5f, 19, 8.5f);
                line(c, 9, 13, 16, 13);
                line(c, 9, 16.5f, 14, 16.5f);
                break;
            case NEWCHAT2:
                c.drawCircle(12, 11.5f, 8.6f, p);
                poly(c, 6.4f, 17.6f, 4f, 21.2f, 10f, 19.8f);
                line(c, 12, 8, 12, 15);
                line(c, 8.5f, 11.5f, 15.5f, 11.5f);
                break;
            case SOUND_OFF:
            case SOUND_ON:
                poly(c, 3.5f, 9.5f, 7.5f, 9.5f, 12.5f, 5.2f, 12.5f, 18.8f, 7.5f, 14.5f, 3.5f, 14.5f, 3.5f, 9.5f);
                r.set(9, 8.4f, 15.5f, 15.6f);
                c.drawArc(r, -50, 100, false, p);
                r.set(7.5f, 5.6f, 19f, 18.4f);
                c.drawArc(r, -50, 100, false, p);
                if (type == SOUND_OFF) line(c, 20.5f, 3.5f, 3.5f, 20.5f);
                break;
            case VOICE:
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(8, 12, 1.5f, p);
                p.setStyle(Paint.Style.STROKE);
                r.set(5.5f, 9.5f, 10.5f, 14.5f);
                r.set(2.8f, 9f, 14.2f, 15f);
                c.drawArc(r, -55, 110, false, p);
                r.set(0.8f, 5.8f, 20f, 18.2f);
                c.drawArc(r, -55, 110, false, p);
                break;
            case SEARCH:
                c.drawCircle(10.8f, 10.8f, 6.6f, p);
                line(c, 15.8f, 15.8f, 20.6f, 20.6f);
                break;
            case PIN:
                poly(c, 8.5f, 4.5f, 15.5f, 4.5f, 14.5f, 10.5f, 18f, 14f, 6f, 14f, 9.5f, 10.5f, 8.5f, 4.5f);
                line(c, 12, 14, 12, 20.5f);
                break;
            case MULTI:
                c.drawCircle(6, 7, 2.2f, p);
                c.drawCircle(6, 16.5f, 2.2f, p);
                line(c, 11.5f, 7, 20, 7);
                line(c, 11.5f, 16.5f, 20, 16.5f);
                break;
            case CAMERA:
                r.set(3, 7, 21, 20);
                c.drawRoundRect(r, 3.2f, 3.2f, p);
                c.drawCircle(12, 13.5f, 3.6f, p);
                poly(c, 8, 7, 9.6f, 4.4f, 14.4f, 4.4f, 16, 7);
                break;
            case IMAGE:
                r.set(3.5f, 4.5f, 20.5f, 19.5f);
                c.drawRoundRect(r, 3.2f, 3.2f, p);
                c.drawCircle(9, 10, 1.8f, p);
                poly(c, 4, 17.5f, 10, 12.8f, 14, 16.5f, 17, 13.8f, 20, 16.5f);
                break;
            case CLIP:
                path.reset();
                path.moveTo(17.2f, 11.2f);
                path.lineTo(10.8f, 17.6f);
                path.quadTo(8.6f, 19.6f, 6.8f, 17.8f);
                path.quadTo(5f, 16f, 7f, 13.8f);
                path.lineTo(14.4f, 6.4f);
                path.quadTo(16.2f, 4.8f, 17.8f, 6.4f);
                path.quadTo(19.3f, 8.1f, 17.8f, 9.8f);
                path.lineTo(10.6f, 17f);
                c.drawPath(path, p);
                break;
            case MORE:
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(5, 12, 1.8f, p);
                c.drawCircle(12, 12, 1.8f, p);
                c.drawCircle(19, 12, 1.8f, p);
                break;
            case MOON:
                path.reset();
                path.moveTo(19.5f, 14f);
                path.cubicTo(18f, 18.2f, 13.5f, 20.5f, 9.2f, 19f);
                path.cubicTo(4.8f, 17.5f, 3f, 12.8f, 5f, 8.8f);
                path.cubicTo(6f, 6.8f, 7.8f, 5.4f, 9.8f, 4.8f);
                path.cubicTo(8.3f, 8.2f, 9f, 12f, 12f, 14.4f);
                path.cubicTo(14.5f, 16.2f, 17.5f, 15.8f, 19.5f, 14f);
                c.drawPath(path, p);
                break;
            case TEXTSIZE:
                poly(c, 2.5f, 19f, 8.2f, 5f, 13.9f, 19f);
                line(c, 4.4f, 14.2f, 12f, 14.2f);
                poly(c, 14.6f, 19f, 17.9f, 10.8f, 21.2f, 19f);
                line(c, 15.9f, 16.6f, 19.9f, 16.6f);
                break;
            case WAND:
                path.reset();
                path.moveTo(9.5f, 3.5f);
                path.quadTo(10.2f, 8.2f, 15f, 9.5f);
                path.quadTo(10.2f, 10.8f, 9.5f, 15.5f);
                path.quadTo(8.8f, 10.8f, 4f, 9.5f);
                path.quadTo(8.8f, 8.2f, 9.5f, 3.5f);
                c.drawPath(path, p);
                line(c, 14f, 14f, 20.5f, 20.5f);
                line(c, 18f, 4f, 18f, 7.4f);
                line(c, 16.3f, 5.7f, 19.7f, 5.7f);
                break;
            case MIC:
                r.set(9, 3.5f, 15, 13.5f);
                c.drawRoundRect(r, 3f, 3f, p);
                r.set(5.8f, 7.5f, 18.2f, 18.5f);
                c.drawArc(r, 0, 180, false, p);
                line(c, 12, 18.5f, 12, 21f);
                break;
            case HELP:
                c.drawCircle(12, 12, 8.6f, p);
                r.set(9.4f, 7.2f, 14.6f, 12.2f);
                c.drawArc(r, 180, 230, false, p);
                line(c, 12, 12.4f, 12, 14f);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 16.4f, 1.1f, p);
                break;
            case LOGOUT:
                poly(c, 14.5f, 4.5f, 19.5f, 4.5f, 19.5f, 19.5f, 14.5f, 19.5f);
                line(c, 3.5f, 12, 14.5f, 12);
                poly(c, 7, 8.2f, 3.5f, 12, 7, 15.8f);
                break;
            case PHONE:
                r.set(7, 3, 17, 21);
                c.drawRoundRect(r, 2.6f, 2.6f, p);
                line(c, 10.6f, 18, 13.4f, 18);
                break;
            case DOC:
                poly(c, 6, 3.5f, 18, 3.5f, 18, 20.5f, 6, 20.5f, 6, 3.5f);
                line(c, 9, 8, 15, 8);
                line(c, 9, 12, 15, 12);
                line(c, 9, 16, 12.5f, 16);
                break;
            case MAIL:
                r.set(3, 5.5f, 21, 18.5f);
                c.drawRoundRect(r, 2.6f, 2.6f, p);
                poly(c, 4, 7.5f, 12, 13.5f, 20, 7.5f);
                break;
            case AVATAR:
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFD9D9DC);
                c.drawCircle(12, 12, 12, p);
                p.setColor(0xFF8E8E93);
                c.drawCircle(12, 9.4f, 3.9f, p);
                path.reset();
                path.moveTo(4.6f, 20f);
                path.quadTo(5.5f, 14f, 12, 14f);
                path.quadTo(18.5f, 14f, 19.4f, 20f);
                path.quadTo(16f, 23f, 12, 23f);
                path.quadTo(8f, 23f, 4.6f, 20f);
                c.drawPath(path, p);
                break;
            case LOGO:
                // Original whale-style mark (not a copy of any brand logo).
                p.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(2.2f, 13.2f);
                path.cubicTo(2.2f, 8.2f, 6.2f, 5.4f, 11.2f, 5.9f);
                path.cubicTo(15.0f, 6.3f, 17.6f, 8.6f, 18.4f, 11.6f);
                path.lineTo(21.8f, 8.2f);
                path.cubicTo(22.6f, 7.4f, 23.2f, 8.0f, 23.0f, 9.0f);
                path.cubicTo(22.6f, 11.6f, 21.4f, 13.6f, 19.4f, 14.8f);
                path.cubicTo(17.4f, 18.4f, 13.4f, 20.2f, 9.2f, 19.6f);
                path.cubicTo(5.2f, 19.0f, 2.2f, 16.6f, 2.2f, 13.2f);
                path.close();
                c.drawPath(path, p);
                p.setShader(null);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(7.2f, 11.4f, 1.15f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.3f);
                p.setColor(Theme.alpha(0xFFFFFFFF, 190));
                path.reset();
                path.moveTo(6.2f, 15.4f);
                path.quadTo(9.6f, 17.4f, 13.4f, 15.2f);
                c.drawPath(path, p);
                break;
        }
        c.restore();
    }
}
