package com.schedule.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class Post(
    val id: Int,
    val text: String
)

@Composable
fun SocialScreen() {
    var posts by remember { mutableStateOf(listOf<Post>()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Text(text = "Social Screen", style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(16.dp))

        AddPostSection(onAdd = { text ->
            posts = posts + Post(posts.size, text)
        })

        Spacer(modifier = Modifier.height(16.dp))

        PostList(posts)
    }
}

@Composable
fun AddPostSection(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Column {
        TextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Enter post") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            if (text.isNotBlank()) {
                onAdd(text)
                text = ""
            }
        }) {
            Text("Add")
        }
    }
}

@Composable
fun PostList(posts: List<Post>) {
    Column {
        posts.forEach {
            PostItem(it)
        }
    }
}

@Composable
fun PostItem(post: Post) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = post.text,
            modifier = Modifier.padding(16.dp)
        )
    }
}
