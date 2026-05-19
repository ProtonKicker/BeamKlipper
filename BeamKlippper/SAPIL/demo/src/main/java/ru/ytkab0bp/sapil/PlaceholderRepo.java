package ru.ytkab0bp.sapil;

import java.util.List;

public interface PlaceholderRepo extends APIRunner {
    PlaceholderRepo INSTANCE = APILibrary.newRunner(PlaceholderRepo.class, new RunnerConfig() {
        @Override
        public String getBaseURL() {
            return "https://jsonplaceholder.typicode.com/";
        }
    });

    @Method("posts")
    void getPosts(APICallback<List<Post>> callback);

    @Method("posts/{}")
    void getPost(int id, APICallback<Post> callback);

    @Method("comments")
    void getComments(@Arg("postId") int postId, APICallback<List<Comment>> callback);

    final class Post {
        public int userId;
        public int id;
        public String title;
        public String body;

        @Override
        public String toString() {
            return "Post{" +
                    "userId=" + userId +
                    ", id=" + id +
                    ", title='" + title + '\'' +
                    ", body='" + body + '\'' +
                    '}';
        }
    }

    final class Comment {
        public int postId;
        public int id;
        public String name;
        public String email;
        public String body;

        @Override
        public String toString() {
            return "Comment{" +
                    "postId=" + postId +
                    ", id=" + id +
                    ", name='" + name + '\'' +
                    ", email='" + email + '\'' +
                    ", body='" + body + '\'' +
                    '}';
        }
    }
}
